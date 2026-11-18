package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.player.Input;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Client half of the Archivist freeze (the server half is DragonFreeze). While an Archivist dragon is
 * in the level
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class EndFreeze {
    private EndFreeze() {}

    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!DimensionMusic.isDragonNear()) return;
        Input input = event.getInput();
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!DimensionMusic.isDragonNear()) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
