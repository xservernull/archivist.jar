package com.xservernull.archivist.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.events.ChildhoodNet;
import com.xservernull.archivist.events.neverforgetyou.NfyNet;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Client side of Hope's conversation
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class NfyClient {
    private NfyClient() {}

    private static final ResourceLocation LAYER =
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "nfy_dialogue");
    private static final long FADE_MS = 300;
    private static final int LINE_HEIGHT = 10;
    public static final KeyMapping LOG_KEY =
            new KeyMapping("key.archivist.log", InputConstants.KEY_K, "key.categories.archivist");

    /**"press K" hint */
    private static final long HINT_DELAY_MS = 4000;
    private static final long HINT_FADE_IN_MS = 1000;
    private static final long HINT_HOLD_MS = 35000;
    private static final long HINT_FADE_OUT_MS = 1500;

    private static boolean showing;
    private static List<String> hudButtons = List.of();
    private static boolean inNfy;
    private static long hintStartMs = -1;
    private static String speaker = "";
    private static String text = "";
    private static long startMs;
    private static long durationMs;
    private static final Deque<String> pendingReads = new ArrayDeque<>();

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, NfyClient::render);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(LOG_KEY);
    }
    public static boolean isHudAsking() { return !hudButtons.isEmpty(); }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        if (hudButtons.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            hudButtons = List.of();
            return;
        }
        if (minecraft.screen != null) return; // a screen in front (pause menu, inventory) gets the keys
        int count = hudButtons.size();
        int picked = -1;
        KeyMapping[] keys = minecraft.options.keyHotbarSlots;
        for (int i = 0; i < keys.length; i++) {
            boolean hit = false;
            while (keys[i].consumeClick()) hit = true;
            if (hit && i < count && picked < 0) picked = i;
        }
        if (picked >= 0) {
            hudButtons = List.of();
            PacketDistributor.sendToServer(new NfyNet.Pick(picked));
        }
    }
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean now = minecraft.level != null && minecraft.level.dimension().equals(Archivist.NEVER_FORGET_YOU);
        if (now && !inNfy) hintStartMs = Util.getMillis();
        if (!now) hintStartMs = -1;
        inNfy = now;
        while (LOG_KEY.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                hintStartMs = -1; // they found the key, the hint is done
                PacketDistributor.sendToServer(new NfyNet.LogRequest());
            }
        }
    }

    public static void log(NfyNet.Log log) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null) minecraft.setScreen(new NfyLogScreen(log.lines()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        showing = false;
        hudButtons = List.of();
        inNfy = false;
        hintStartMs = -1;
        pendingReads.clear();
    }

    public static void say(NfyNet.Say line) {
        speaker = line.speaker();
        text = line.text();
        startMs = Util.getMillis();
        durationMs = Math.max(1000L, line.ticks() * 50L);
        showing = true;
    }
    public static void ask(NfyNet.Ask ask) {
        if (ask.buttons().isEmpty()) return;
        Minecraft.getInstance().setScreen(new NfyChoiceScreen(ask.buttons(), NfyNet.Pick::new));
    }
    public static void askHud(List<String> buttons) {
        if (buttons.isEmpty()) return;
        hudButtons = List.copyOf(buttons);
    }
    public static void askDog(List<String> buttons) {
        if (buttons.isEmpty()) return;
        Minecraft.getInstance().setScreen(new NfyChoiceScreen(buttons, ChildhoodNet.DogPick::new));
    }

    public static void read(NfyNet.Read read) {
        pendingReads.add(read.text());
        openPendingRead(Minecraft.getInstance());
    }

    private static void openPendingRead(Minecraft minecraft) {
        if (pendingReads.isEmpty() || minecraft.screen != null) return;
        minecraft.setScreen(new NfyReadScreen(pendingReads.poll()));
    }

    public static void clear() {
        showing = false;
        hudButtons = List.of();
        pendingReads.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof NfyChoiceScreen || minecraft.screen instanceof NfyReadScreen
                || minecraft.screen instanceof NfyLogScreen) minecraft.setScreen(null);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        openPendingRead(minecraft); // the next queued message line, once no screen is open
        renderHint(graphics, minecraft);
        renderHudChoices(graphics, minecraft);
        if (!showing) return;
        long elapsed = Util.getMillis() - startMs;
        boolean holding = minecraft.screen instanceof NfyChoiceScreen || !hudButtons.isEmpty();
        float alpha = 1.0F;
        if (elapsed < FADE_MS) {
            alpha = elapsed / (float) FADE_MS;
        } else if (!holding && elapsed > durationMs - FADE_MS) {
            alpha = (durationMs - elapsed) / (float) FADE_MS;
        }
        if (!holding && elapsed >= durationMs) {
            showing = false;
            return;
        }
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 255);
        if (a < 4) return; // vanilla draws text with alpha under 4 as opaque

        MutableComponent line = Component.empty();
        if (!speaker.isEmpty()) {
            ChatFormatting colour = speaker.equals("you") ? ChatFormatting.GRAY : ChatFormatting.YELLOW;
            String shown = Lang.lookup("archivist.speaker." + speaker);
            line.append(Component.literal(shown != null ? shown : speaker).withStyle(colour)).append(Component.literal(": ").withStyle(ChatFormatting.WHITE));
            String said = Lang.get(text);
            if (speaker.equals("you")) said = said.replaceFirst("^>\\s*", "");
            line.append(Component.literal(said).withStyle(ChatFormatting.WHITE));
        } else {
            line.append(Lang.text(text).withStyle(ChatFormatting.ITALIC));
        }
        int maxWidth = Math.min(graphics.guiWidth() - 40, 320);
        List<FormattedCharSequence> wrapped = minecraft.font.split(line, maxWidth);
        int centerX = graphics.guiWidth() / 2;
        int startY = graphics.guiHeight() / 3 - ((wrapped.size() - 1) * LINE_HEIGHT) / 2;
        int color = (a << 24) | 0xFFFFFF;
        for (int i = 0; i < wrapped.size(); i++) {
            graphics.drawCenteredString(minecraft.font, wrapped.get(i), centerX, startY + i * LINE_HEIGHT, color);
        }
    }
    private static void renderHudChoices(GuiGraphics graphics, Minecraft minecraft) {
        if (hudButtons.isEmpty() || minecraft.screen != null) return;
        KeyMapping[] keys = minecraft.options.keyHotbarSlots;
        int maxWidth = Math.min(graphics.guiWidth() - 40, 320);
        int y = graphics.guiHeight() / 2 + 10;
        for (int i = 0; i < hudButtons.size(); i++) {
            String key = i < keys.length ? keys[i].getTranslatedKeyMessage().getString() : String.valueOf(i + 1);
            MutableComponent label = Component.literal("[" + key + "] ").withStyle(ChatFormatting.YELLOW)
                    .append(Lang.text(hudButtons.get(i)).withStyle(ChatFormatting.WHITE));
            List<FormattedCharSequence> wrapped = minecraft.font.split(label, maxWidth);
            int boxH = wrapped.size() * LINE_HEIGHT + 6;
            int boxW = 0;
            for (FormattedCharSequence part : wrapped) boxW = Math.max(boxW, minecraft.font.width(part));
            boxW += 12;
            int x = (graphics.guiWidth() - boxW) / 2;
            graphics.fill(x, y, x + boxW, y + boxH, 0xA0000000);
            for (int l = 0; l < wrapped.size(); l++) {
                graphics.drawString(minecraft.font, wrapped.get(l), x + 6, y + 3 + l * LINE_HEIGHT, 0xFFFFFFFF, false);
            }
            y += boxH + 4;
        }
    }
    private static void renderHint(GuiGraphics graphics, Minecraft minecraft) {
        if (hintStartMs < 0 || minecraft.screen != null) return;
        long t = Util.getMillis() - hintStartMs - HINT_DELAY_MS;
        if (t < 0) return;
        long total = HINT_FADE_IN_MS + HINT_HOLD_MS + HINT_FADE_OUT_MS;
        if (t >= total) {
            hintStartMs = -1;
            return;
        }
        float alpha;
        if (t < HINT_FADE_IN_MS) alpha = t / (float) HINT_FADE_IN_MS;
        else if (t < HINT_FADE_IN_MS + HINT_HOLD_MS) alpha = 1.0F;
        else alpha = (total - t) / (float) HINT_FADE_OUT_MS;
        int a = (int) (Math.max(0.0F, Math.min(1.0F, alpha)) * 200);
        if (a < 4) return; // vanilla draws text with alpha under 4 as opaque
        Component hint = Lang.text("archivist.nfy.log_hint|Press %s to read the lines you have found", LOG_KEY.getTranslatedKeyMessage().getString())
                .withStyle(ChatFormatting.ITALIC);
        graphics.drawCenteredString(minecraft.font, hint, graphics.guiWidth() / 2, graphics.guiHeight() - 70,
                (a << 24) | 0xB8C0E0);
    }
}
