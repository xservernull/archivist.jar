// m4kb_world lag spike: for about 11 seconds the server saves your spot every second and now
// and then throws you back to it, like a bad frame.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

final class M4kbLagSpike implements QuickEvent {
    private static final String ANCHOR = "m4kb_lag_anchor";
    private static final int DURATION = 220, STEP = 20, YANK_AT = 9, MIN_MOVED_SQ = 4;

    @Override public String id() { return "m4kb_lag_spike"; }

    @Override
    public void start(ServerPlayer player) {
        save(player);
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 1.5F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        int phase = age % STEP;
        if (phase == 0) {
            save(player);
        } else if (phase == YANK_AT && player.getRandom().nextInt(3) != 0) {
            yank(player);
        }
        return age >= DURATION;
    }

    private static void save(ServerPlayer player) {
        Events.memory(player).setCooldown(ANCHOR, player.blockPosition().asLong());
    }

    private static void yank(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos home = BlockPos.of(Events.memory(player).cooldownUntil(ANCHOR));
        Vec3 to = Vec3.atBottomCenterOf(home);
        if (to.distanceToSqr(player.position()) < MIN_MOVED_SQ) return;
        if (!level.noCollision(player, player.getBoundingBox().move(to.subtract(player.position())))) return;
        player.fallDistance = 0;
        player.teleportTo(level, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        level.playSound(null, home, SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.8F, 0.6F);
        EventNet.fx(player, "infdev_arrive", 6);
    }
}
