package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * small grey text in the top left,¿
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class CornerHud {
    private CornerHud() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "corner_hud");
    private static final LocalDate DAY_ZERO = LocalDate.of(2010, 12, 10);
    private static final float SCALE = 0.8f;
    private static final int GREY = 0xAAAAAA;
    private static final int WRAP = 200;
    private static final long FADE_MS = 500;
    private static final int COUNTDOWN_TICKS = 20 * 20;
    private static String objective = "";
    private static boolean clearing;
    private static long changedMs;
    private static boolean inQuietPlace;
    private static int countdown;
    public static void setObjective(String text) {
        if (text.isEmpty()) {
            if (objective.isEmpty() || clearing) return;
            clearing = true;
        } else {
            objective = text;
            clearing = false;
        }
        changedMs = Util.getMillis();
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, CornerHud::render);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean now = minecraft.level != null && minecraft.level.dimension().equals(Archivist.QUIET_PLACE);
        if (now && !inQuietPlace) countdown = COUNTDOWN_TICKS;
        if (!now) countdown = 0;
        else if (countdown > 0 && !minecraft.isPaused()) countdown--;
        inQuietPlace = now;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        objective = "";
        clearing = false;
        inQuietPlace = false;
        countdown = 0;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.options.hideGui) return;
        Font font = minecraft.font;
        int line = (int) (font.lineHeight / SCALE);
        int x = (int) (4 / SCALE);
        int y = x;
        graphics.pose().pushPose();
        graphics.pose().scale(SCALE, SCALE, 1f);

        graphics.drawString(font, Lang.fmt("archivist.hud.day|day %s", ChronoUnit.DAYS.between(DAY_ZERO, LocalDate.now())), x, y, GREY, false);
        y += line + 2;

        if (countdown > 0) {
            int centis = (int) Math.ceil(countdown * 5.0);
            graphics.drawString(font, String.format("%02d.%02d", centis / 100, centis % 100), x, y, GREY, false);
            y += line + 2;
            graphics.drawString(font, Lang.get("archivist.hud.shrine_task|find the items and build his shrine."), x, y, GREY, false);
            y += line + 1;
            graphics.drawString(font, Lang.get("archivist.hud.dont_be_found|dont be found."), x, y, GREY, false);
            y += line + 2;
        }

        if (!objective.isEmpty()) {
            float alpha = Math.min(1f, (Util.getMillis() - changedMs) / (float) FADE_MS);
            if (clearing) {
                alpha = 1f - alpha;
                if (alpha <= 0f) {
                    objective = "";
                    clearing = false;
                }
            }
            int a = (int) (alpha * 255);
            if (a > 4 && !objective.isEmpty()) {
                List<FormattedCharSequence> lines = font.split(FormattedText.of(Lang.get(objective)), WRAP);
                for (FormattedCharSequence part : lines) {
                    graphics.drawString(font, part, x, y, (a << 24) | GREY, false);
                    y += line;
                }
            }
        }
        graphics.pose().popPose();
    }
}
