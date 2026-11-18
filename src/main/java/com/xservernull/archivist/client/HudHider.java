package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.events.RetroMovement;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Set;

/**
 * Hides the survival HUD in xor_world, m4kb_world and quietplace
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class HudHider {
    private HudHider() {}
    private static final Set<ResourceLocation> HIDDEN = Set.of(
            VanillaGuiLayers.HOTBAR,
            VanillaGuiLayers.SELECTED_ITEM_NAME,
            VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.FOOD_LEVEL,
            VanillaGuiLayers.ARMOR_LEVEL,
            VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL,
            VanillaGuiLayers.AIR_LEVEL
    );

    @SubscribeEvent
    public static void onLayer(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (DimensionMusic.isDragonNear() && (HIDDEN.contains(event.getName()) || event.getName().equals(VanillaGuiLayers.CROSSHAIR))) {
            event.setCanceled(true);
            return;
        }
        if (!HIDDEN.contains(event.getName())) return;
        var dimension = minecraft.level.dimension();
        if (RetroMovement.isRetro(dimension) || dimension == Archivist.QUIET_PLACE) event.setCanceled(true);
    }
}
