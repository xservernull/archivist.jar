package com.xservernull.archivist.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xservernull.archivist.Archivist;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * draws VideoPlayer frame over the whole screen
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class VideoOverlay {
    private VideoOverlay() {}

    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "video");

    @SubscribeEvent
    public static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelowAll(LAYER, VideoOverlay::render); // below the HUD layers
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        VideoPlayer.tick();
        if (!VideoPlayer.ready()) return;
        int screenW = minecraft.getWindow().getGuiScaledWidth(), screenH = minecraft.getWindow().getGuiScaledHeight();
        int videoW = VideoPlayer.width(), videoH = VideoPlayer.height();
        int w = screenW, h = screenH, x = 0, y = 0;
        if (videoW > 0 && videoH > 0) {
            float scale = Math.max((float) screenW / videoW, (float) screenH / videoH);
            w = (int) (videoW * scale);
            h = (int) (videoH * scale);
            x = (screenW - w) / 2;
            y = (screenH - h) / 2;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(VideoPlayer.TEXTURE, x, y, w, h, 0F, 0F, videoW, videoH, videoW, videoH);
        RenderSystem.disableBlend();
    }
}