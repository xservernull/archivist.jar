// old_nether keep moving: the heat sinks into anything that stands still.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;

final class OldNetherKeepMoving implements QuickEvent {
    private static final String POS = "tmp_old_nether_move_pos", HITS = "tmp_old_nether_move_hits";
    private static final int DURATION = 20 * 25, CHECK = 20, MAX_HITS = 3;

    @Override public String id() { return "old_nether_keep_moving"; }

    @Override
    public void start(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        memory.setCooldown(POS, player.blockPosition().asLong());
        memory.setCooldown(HITS, 0L);
        player.sendSystemMessage(Texts.tr("archivist.event.old_nether_keep_moving.start", "the heat finds whatever stands still. keep moving."));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.LAVA_AMBIENT, SoundSource.AMBIENT, 0.8F, 0.6F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        EventState.Memory memory = Events.memory(player);
        if (age % CHECK == 0) {
            BlockPos last = BlockPos.of(memory.cooldownUntil(POS));
            BlockPos here = player.blockPosition();
            memory.setCooldown(POS, here.asLong());
            long hits = memory.cooldownUntil(HITS);
            if (last.distSqr(here) <= 1.0 && hits < MAX_HITS && player.getHealth() > 8.0F && !player.isCreative()) {
                memory.setCooldown(HITS, hits + 1);
                player.hurt(player.damageSources().hotFloor(), 1.0F);
                player.displayClientMessage(Component.literal("§cmove"), true);
            }
        }
        if (age >= DURATION) {
            MarkerHunt.effect(player, MobEffects.FIRE_RESISTANCE, 20 * 300, 0);
            player.sendSystemMessage(Texts.tr("archivist.event.old_nether_keep_moving.win", "the heat lets go of you."));
            return true;
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        memory.setCooldown(POS, 0L);
        memory.setCooldown(HITS, 0L);
    }
}
