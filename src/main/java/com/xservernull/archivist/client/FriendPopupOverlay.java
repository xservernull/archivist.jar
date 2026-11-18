package com.xservernull.archivist.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * the Friend popup flood
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class FriendPopupOverlay {
    private FriendPopupOverlay() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "friend_popup");
    private static final ResourceLocation FRIEND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "textures/entity/friend.png");

    private static final float INVERT_CHANCE = 0.2F, FLICKER_CHANCE = 0.01F;
    private static final int WINDOW_W = 60, WINDOW_H = 68, TITLEBAR_H = 12, IMAGE_SIZE = 44;
    private static final int TITLEBAR = 0xFF0A246A, TITLE_TEXT = 0xFFFFFFFF, BODY = 0xFFECE9D8, BORDER = 0xFF404040, CLOSE = 0xFFD64545;
    private static final String[] TITLES = {
            "friend.exe", "archivist.friend.title.1|look at me", "IMG_0001.png", "archivist.friend.title.2|hello",
            "archivist.friend.title.3|new message", "photo.jpg", "archivist.friend.title.4|u there?",
            "archivist.friend.title.5|come find me", "archivist.friend.title.6|System Message"
    };
    private static final int START_INTERVAL = 5, MIN_INTERVAL = 1, RAMP_TICKS = 600, TOTAL_TICKS = 600, MAX_WINDOWS = 400;

    private static final class Popup {
        final int x, y;
        final String title;
        boolean inverted;

        Popup(int x, int y, String title, boolean inverted) {
            this.x = x;
            this.y = y;
            this.title = title;
            this.inverted = inverted;
        }
    }

    private static final List<Popup> WINDOWS = new ArrayList<>();
    private static final Random RNG = new Random();
    private static boolean active, whiteNext = true;
    private static int elapsed, untilNext;
    public static void trigger() {
        active = true;
        elapsed = 0;
        untilNext = START_INTERVAL;
        whiteNext = true;
        WINDOWS.clear();
        DimensionMusic.setFriendEvent(true);
        launchDesktopPopup();
    }


    private static void launchDesktopPopup() {
        try (InputStream source = FriendPopupOverlay.class.getResourceAsStream("/assets/archivist/binary/FriendPopup.jar")) {
            if (source == null) {
                LOG.warn("[friend] FriendPopup.jar is not in the mod, no desktop popup");
                return;
            }
            Path dir = Path.of(System.getProperty("java.io.tmpdir"), Archivist.MOD_ID);
            Files.createDirectories(dir);
            Path jar = dir.resolve("FriendPopup.jar");
            byte[] bytes = source.readAllBytes();
            if (!Files.exists(jar) || !Arrays.equals(Files.readAllBytes(jar), bytes)) Files.write(jar, bytes);
            String java = ProcessHandle.current().info().command().orElse(null);
            if (java == null) {
                LOG.warn("[friend] could not find the JVM path, no desktop popup");
                return;
            }
            new ProcessBuilder(java, "-jar", jar.toAbsolutePath().toString())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            LOG.info("[friend] desktop popup started from {}", jar);
        } catch (IOException | RuntimeException e) {
            LOG.warn("[friend] desktop popup failed: {}", e.toString());
        }
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, FriendPopupOverlay::render);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        WINDOWS.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        int width = minecraft.getWindow().getGuiScaledWidth(), height = minecraft.getWindow().getGuiScaledHeight();
        elapsed++;
        if (elapsed < TOTAL_TICKS && WINDOWS.size() < MAX_WINDOWS && --untilNext <= 0) {
            WINDOWS.add(new Popup(RNG.nextInt(Math.max(1, width - WINDOW_W)), RNG.nextInt(Math.max(1, height - WINDOW_H)),
                    Lang.get(TITLES[RNG.nextInt(TITLES.length)]), RNG.nextFloat() < INVERT_CHANCE));
            spamChat(minecraft);
            untilNext = interval();
        }
        if (elapsed >= TOTAL_TICKS) {
            WINDOWS.clear();
            active = false;
            DimensionMusic.setFriendEvent(false);
            return;
        }
        if (!WINDOWS.isEmpty() && RNG.nextFloat() < FLICKER_CHANCE) {
            Popup popup = WINDOWS.get(RNG.nextInt(WINDOWS.size()));
            popup.inverted = !popup.inverted;
        }
    }

    private static int interval() {
        if (elapsed >= RAMP_TICKS) return MIN_INTERVAL;
        float progress = elapsed / (float) RAMP_TICKS;
        return Math.round(START_INTERVAL - (START_INTERVAL - MIN_INTERVAL) * progress);
    }

    private static void spamChat(Minecraft minecraft) {
        ChatFormatting color = whiteNext ? ChatFormatting.WHITE : ChatFormatting.BLACK;
        whiteNext = !whiteNext;
        minecraft.gui.getChat().addMessage(Component.literal("youareanidiot").setStyle(Style.EMPTY.withColor(color)));
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.player == null || minecraft.options.hideGui) return;
        for (Popup popup : WINDOWS) draw(graphics, minecraft, popup);
    }

    private static void draw(GuiGraphics graphics, Minecraft minecraft, Popup popup) {
        int x = popup.x, y = popup.y;
        graphics.fill(x - 1, y - 1, x + WINDOW_W + 1, y + WINDOW_H + 1, BORDER);
        graphics.fill(x, y + TITLEBAR_H, x + WINDOW_W, y + WINDOW_H, BODY);
        graphics.fill(x, y, x + WINDOW_W, y + TITLEBAR_H, TITLEBAR);
        graphics.drawString(minecraft.font, popup.title, x + 4, y + 3, TITLE_TEXT, false);
        int closeX = x + WINDOW_W - 12;
        graphics.fill(closeX, y + 2, closeX + 10, y + 12, CLOSE);
        graphics.drawString(minecraft.font, "x", closeX + 3, y + 3, TITLE_TEXT, false);
        int imageX = x + (WINDOW_W - IMAGE_SIZE) / 2, imageY = y + TITLEBAR_H + 4;
        RenderSystem.enableBlend();
        if (popup.inverted) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ZERO,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        }
        graphics.blit(FRIEND_TEXTURE, imageX, imageY, 0, 0, IMAGE_SIZE, IMAGE_SIZE, IMAGE_SIZE, IMAGE_SIZE);
        if (popup.inverted) RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
}
