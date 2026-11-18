// old_nether: piglins always chase you. every half second, every zombified piglin with ai
// within #range blocks of a player in old_nether that has no live target is pointed at that

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class OldNetherPiglins {
    private OldNetherPiglins() {}

    private static final ResourceLocation OLD_NETHER = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "old_nether");
    private static final int EVERY = 10;
    private static final double RANGE = 48.0;

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        if (player.tickCount % EVERY != 0 || !player.isAlive() || player.isSpectator() || player.isCreative()) return;
        if (!player.level().dimension().location().equals(OLD_NETHER)) return;
        for (ZombifiedPiglin piglin : player.serverLevel().getEntitiesOfClass(ZombifiedPiglin.class, player.getBoundingBox().inflate(RANGE), p -> !p.isNoAi())) {
            if (piglin.getTarget() != null && piglin.getTarget().isAlive()) continue;
            piglin.setTarget(player);
            piglin.setPersistentAngerTarget(player.getUUID());
            piglin.startPersistentAngerTimer();
        }
    }
}
