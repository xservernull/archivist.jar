package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.item.DetectorMapItem;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * the detector map radar
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class DetectorMapOverlay {
    private DetectorMapOverlay() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "detector_map");
    private static final int RADIUS = 34, MARGIN = 14;
    private static final double RANGE = 64.0;
    private static final long FADE_MS = 220;
    private static final long SWEEP_MS = 3600;
    private static final int GLASS = 0xB00B0F14;
    private static final int RIM = 0x66E6EDF3;
    private static final int GUIDE = 0x1FFFFFFF;
    private static final int PLAYER = 0xFFFFFFFF;
    private static final int TEXT = 0xFFE6EDF3;
    private static final int TEXT_DIM = 0x99E6EDF3;
    private static final int SHRINE = 0xFFFFC454;
    private static final int PORTAL = 0xFFB794FF;

    private static List<BlockPos> markers = List.of();
    private static final Map<BlockPos, Long> FIRST_SEEN = new HashMap<>();
    private static long raisedMs = -1;

    public static void set(List<BlockPos> positions) {
        markers = List.copyOf(positions);
        FIRST_SEEN.keySet().retainAll(markers);
    }

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, DetectorMapOverlay::render);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        markers = List.of();
        FIRST_SEEN.clear();
        raisedMs = -1;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui
                || !DetectorMapItem.isHeld(player)) {
            raisedMs = -1;
            return;
        }
        ResourceKey<Level> dimension = minecraft.level.dimension();
        boolean quiet = dimension.equals(Archivist.QUIET_PLACE);
        if (!quiet && !dimension.equals(Level.OVERWORLD)) {
            raisedMs = -1;
            return;
        }
        long now = Util.getMillis();
        if (raisedMs < 0) raisedMs = now;
        float fade = Mth.clamp((now - raisedMs) / (float) FADE_MS, 0.0F, 1.0F);
        fade = fade * fade * (3.0F - 2.0F * fade);

        int accent = quiet ? SHRINE : PORTAL;
        int cx = graphics.guiWidth() - RADIUS - MARGIN;
        int cy = graphics.guiHeight() - RADIUS - MARGIN - 10;
        float yaw = player.getViewYRot(deltaTracker.getGameTimeDeltaPartialTick(false));
        double cos = Math.cos(Math.toRadians(yaw)), sin = Math.sin(Math.toRadians(yaw));
        Font font = minecraft.font;

        // glass, guides, rim
        disc(graphics, cx, cy, RADIUS, tint(GLASS, fade));
        ring(graphics, cx, cy, RADIUS * 2 / 3, 1, tint(GUIDE, fade));
        ring(graphics, cx, cy, RADIUS / 3, 1, tint(GUIDE, fade));
        graphics.fill(cx, cy - RADIUS + 2, cx + 1, cy + RADIUS - 2, tint(GUIDE, fade));
        graphics.fill(cx - RADIUS + 2, cy, cx + RADIUS - 2, cy + 1, tint(GUIDE, fade));
        sweep(graphics, cx, cy, accent, now, fade);
        ring(graphics, cx, cy, RADIUS, 1, tint(RIM, fade));
        // a notch above the rim: the way you look
        graphics.fill(cx - 2, cy - RADIUS - 3, cx + 3, cy - RADIUS - 2, tint(accent, fade));
        graphics.fill(cx - 1, cy - RADIUS - 2, cx + 2, cy - RADIUS - 1, tint(accent, fade));

        // north: turns with the world (north is -Z)
        smallText(graphics, font, "N", cx + (int) Math.round(sin * (RADIUS - 6)),
                cy + (int) Math.round(cos * (RADIUS - 6)) - 3, tint(TEXT_DIM, fade), 0.7F);

        // markers
        double best = Double.MAX_VALUE;
        for (BlockPos marker : markers) {
            double dx = marker.getX() + 0.5 - player.getX();
            double dz = marker.getZ() + 0.5 - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            best = Math.min(best, distance);
            double right = dx * -cos + dz * -sin;
            double forward = dx * -sin + dz * cos;
            if (distance > RANGE) {
                FIRST_SEEN.remove(marker);
                chevron(graphics, cx, cy, right, forward, tint(accent, fade * 0.9F));
                continue;
            }
            long seen = FIRST_SEEN.computeIfAbsent(marker, key -> now);
            float appear = Mth.clamp((now - seen) / 300.0F, 0.0F, 1.0F);
            double period = 500.0 + 900.0 * Math.min(1.0, distance / RANGE);
            float pulse = (float) (0.5 + 0.5 * Math.sin(now / period * Math.PI * 2.0));
            double scale = (RADIUS - 5) / RANGE;
            int px = cx + (int) Math.round(right * scale);
            int py = cy - (int) Math.round(forward * scale);
            float a = fade * appear;
            disc(graphics, px, py, 5, tint(accent & 0x00FFFFFF | 0x28000000, a * (0.4F + 0.6F * pulse)));
            disc(graphics, px, py, 3, tint(accent & 0x00FFFFFF | 0x66000000, a));
            disc(graphics, px, py, 2, tint(accent, a));
            graphics.fill(px, py, px + 1, py + 1, tint(0xFFFFFFFF, a));
        }

        // you: a small arrow, always pointing up
        graphics.fill(cx, cy - 3, cx + 1, cy - 2, tint(PLAYER, fade));
        graphics.fill(cx - 1, cy - 2, cx + 2, cy - 1, tint(PLAYER, fade));
        graphics.fill(cx - 2, cy - 1, cx + 3, cy, tint(PLAYER, fade));
        graphics.fill(cx - 2, cy, cx - 1, cy + 1, tint(PLAYER, fade));
        graphics.fill(cx + 2, cy, cx + 3, cy + 1, tint(PLAYER, fade));

        // title above distance below
        if (!quiet) smallText(graphics, font, Lang.get("archivist.detector.portal|PORTAL"), cx, cy - RADIUS - 11, tint(accent, fade), 0.75F);
        String line = markers.isEmpty() ? Lang.get("archivist.detector.no_signal|no signal") : (int) best + " m";
        smallText(graphics, font, line, cx, cy + RADIUS + 5, tint(markers.isEmpty() ? TEXT_DIM : TEXT, fade), 0.85F);
    }

    // ------------------------------------------------------------------ pieces
    private static void sweep(GuiGraphics graphics, int cx, int cy, int accent, long now, float fade) {
        double head = (now % SWEEP_MS) / (double) SWEEP_MS * Math.PI * 2.0;
        int steps = 16;
        for (int k = 0; k < steps; k++) {
            double angle = head - k * 0.045;
            float alpha = (k == 0 ? 0.55F : 0.20F * (1.0F - k / (float) steps)) * fade;
            int color = tint(accent & 0x00FFFFFF | 0xFF000000, alpha);
            for (int r = 3; r < RADIUS - 1; r++) {
                int x = cx + (int) Math.round(Math.cos(angle) * r);
                int y = cy + (int) Math.round(Math.sin(angle) * r);
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }
    private static void chevron(GuiGraphics graphics, int cx, int cy, double right, double forward, int color) {
        double length = Math.sqrt(right * right + forward * forward);
        if (length < 1.0E-4) return;
        double ux = right / length, uy = -forward / length;
        double px = -uy, py = ux;
        for (int s = 0; s < 4; s++) {
            double bx = cx + ux * (RADIUS - 9 + s * 1.5), by = cy + uy * (RADIUS - 9 + s * 1.5);
            int spread = 3 - s;
            for (int k = -spread; k <= spread; k += 2) {
                int x = (int) Math.round(bx + px * k * 0.7), y = (int) Math.round(by + py * k * 0.7);
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private static void disc(GuiGraphics graphics, int cx, int cy, int radius, int argb) {
        if ((argb >>> 24) == 0) return;
        for (int dy = -radius; dy < radius; dy++) {
            double v = dy + 0.5;
            int half = (int) Math.round(Math.sqrt(Math.max(0.0, radius * (double) radius - v * v)));
            graphics.fill(cx - half, cy + dy, cx + half, cy + dy + 1, argb);
        }
    }
    private static void ring(GuiGraphics graphics, int cx, int cy, int radius, int thickness, int argb) {
        if ((argb >>> 24) == 0) return;
        double inner = radius - thickness;
        for (int dy = -radius; dy < radius; dy++) {
            double v = dy + 0.5;
            int outerHalf = (int) Math.round(Math.sqrt(Math.max(0.0, radius * (double) radius - v * v)));
            double innerSq = inner * inner - v * v;
            if (innerSq <= 0.0) {
                graphics.fill(cx - outerHalf, cy + dy, cx + outerHalf, cy + dy + 1, argb);
            } else {
                int innerHalf = (int) Math.round(Math.sqrt(innerSq));
                graphics.fill(cx - outerHalf, cy + dy, cx - innerHalf, cy + dy + 1, argb);
                graphics.fill(cx + innerHalf, cy + dy, cx + outerHalf, cy + dy + 1, argb);
            }
        }
    }
    private static void smallText(GuiGraphics graphics, Font font, String text, int x, int y, int argb, float scale) {
        if ((argb >>> 24) < 8) return; // fully faded: vanilla treats a very low alpha as opaque
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawCenteredString(font, text, 0, 0, argb);
        graphics.pose().popPose();
    }
    private static int tint(int argb, float k) {
        int alpha = Mth.clamp(Math.round((argb >>> 24) * k), 0, 255);
        return alpha << 24 | (argb & 0x00FFFFFF);
    }
}
