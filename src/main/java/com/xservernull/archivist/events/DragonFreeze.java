// players near an archivistdragon (within #radius blocks) cannot move, jump, attack or use
// anything (old archivistdragon speed freeze and inputlocker).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.ArchivistDragon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class DragonFreeze {
    private DragonFreeze() {}

    private static final double RADIUS = 64.0;
    private static final int EVERY = 5;
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "dragon_freeze");

    static void listen() {
        NeoForge.EVENT_BUS.addListener(DragonFreeze::onTick);
        NeoForge.EVENT_BUS.addListener(DragonFreeze::onJump);
        NeoForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
            if (frozen(e.getEntity())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (frozen(e.getEntity())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem e) -> {
            if (frozen(e.getEntity())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract e) -> {
            if (frozen(e.getEntity())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock e) -> {
            if (frozen(e.getEntity())) e.setCanceled(true);
        });
    }

    private static boolean frozen(Player player) {
        if (player instanceof ServerPlayer serverPlayer && TheEnd.TALK.equals(Events.running(serverPlayer))) return true;
        return !player.level().getEntitiesOfClass(ArchivistDragon.class, player.getBoundingBox().inflate(RADIUS)).isEmpty();
    }

    private static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % EVERY != 0) return;
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.hasModifier(ID);
        boolean on = frozen(player);
        if (on && !has) speed.addTransientModifier(new AttributeModifier(ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        else if (!on && has) speed.removeModifier(ID);
    }

    private static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player) || !frozen(player)) return;
        player.setDeltaMovement(player.getDeltaMovement().multiply(1, 0, 1));
    }
}
