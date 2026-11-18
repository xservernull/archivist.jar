// m4kb_world jump quota: the world asks for 8 jumps within 20 seconds (counted from the
// player's own jump statistic, so nothing is stored but the starting number).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;

final class M4kbJumpQuota implements QuickEvent {
    private static final String BASE = "tmp_m4kb_jump_base";
    private static final int GOAL = 8, DURATION = 20 * 20;

    @Override public String id() { return "m4kb_jump_quota"; }

    private static int jumps(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
    }

    @Override
    public void start(ServerPlayer player) {
        Events.memory(player).setCooldown(BASE, jumps(player));
        player.sendSystemMessage(Texts.tr("archivist.event.m4kb_jump_quota.start", "the build wants %s jumps. now.", GOAL));
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 1.2F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        int done = jumps(player) - (int) Events.memory(player).cooldownUntil(BASE);
        if (age % 10 == 0) player.displayClientMessage(Component.literal("§7" + Math.min(done, GOAL) + " / " + GOAL), true);
        if (done >= GOAL) {
            Gifts.give(player, Gifts.M4KB_NOTES);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.6F);
            player.sendSystemMessage(Texts.tr("archivist.event.m4kb_jump_quota.win", "quota met."));
            return true;
        }
        if (age >= DURATION) {
            player.sendSystemMessage(Texts.tr("archivist.event.m4kb_jump_quota.fail", "not enough."));
            return true;
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        Events.memory(player).setCooldown(BASE, 0L);
    }
}
