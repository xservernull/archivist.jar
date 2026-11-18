package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * herobrine's proximity in the Quiet Place hunt
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class HerobrineHud {
    private HerobrineHud() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "herobrine_hud");
    private static final int X = 8, Y = 8, WIDTH = 4, HEIGHT = 16, GAP = 3, TOTAL = 8, FADE_TICKS = 60;
    private static final int OFF = 0xFF222222, FAR = 0xFFAA3333, NEAR = 0xFFFF2222, LABEL = 0xDDDDDD;
    private static int phase;
    private static int bars;
    private static float shown;
    private static int fade;

    public static void set(int newPhase, int newBars) {
        phase = newPhase;
        bars = newBars;
        if (newBars > 0) fade = FADE_TICKS;
        if (newPhase == 0) reset();
    }
    private static void reset() {
        bars = 0;
        shown = 0f;
        fade = 0;
    }
    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, HerobrineHud::render);
    }
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (phase != 2) return;
        if (bars == 0 && fade > 0) fade--;
        shown += (bars - shown) * 0.15f;
        if (bars == 0 && fade == 0) shown = 0f;
    }
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        phase = 0;
        reset();
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (phase == 0 || minecraft.player == null || minecraft.options.hideGui) return;
        if (phase == 1) {
            graphics.drawString(minecraft.font, "...", X, Y, 0xFFDDDDDD, false);
            return;
        }
        if (bars == 0 && fade == 0) return;
        float alpha = bars > 0 ? 1.0f : Math.max(0f, fade / (float) FADE_TICKS);
        if (alpha <= 0f) return;
        int filled = Math.round(shown);
        for (int i = 0; i < TOTAL; i++) {
            int h = HEIGHT + i * 2;
            int x = X + i * (WIDTH + GAP);
            int y = Y + (HEIGHT + 8) - h;
            int color = i >= filled ? OFF : i < 4 ? FAR : NEAR;
            graphics.fill(x, y, x + WIDTH, y + h, fade(color, alpha));
        }
        int labelAlpha = (int) (alpha * 0xFF);
        graphics.drawString(minecraft.font, filled >= 5 ? Lang.get("archivist.hud.he_here|he's here") : Lang.get("archivist.hud.he_afar|he's afar"), X, Y + HEIGHT + 20, (labelAlpha << 24) | LABEL, false);
    }

    private static int fade(int argb, float alpha) {
        return ((int) (((argb >> 24) & 0xFF) * alpha) << 24) | (argb & 0x00FFFFFF);
    }
}
