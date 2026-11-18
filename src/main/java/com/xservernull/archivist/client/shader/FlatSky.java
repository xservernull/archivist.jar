package com.xservernull.archivist.client.shader;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Client-only: gives a dimension a flat sky colour.
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class FlatSky {
    private FlatSky() {}

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        OldLook old = OldLook.get(mc.level.dimension());
        if (old != null) {
            event.setRed(old.r());
            event.setGreen(old.g());
            event.setBlue(old.b());
            return;
        }
        Look look = Looks.get(mc.level.dimension());
        if (look == null || look.sky == null) return;
        event.setRed(look.sky.x);
        event.setGreen(look.sky.y);
        event.setBlue(look.sky.z);
    }
    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (OldLook.current() == null) return;
        if (event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) return;
        float far = event.getFarPlaneDistance();
        event.setNearPlaneDistance(far * OldLook.FOG_START);
        event.setFarPlaneDistance(far);
        event.setCanceled(true);
    }
}
