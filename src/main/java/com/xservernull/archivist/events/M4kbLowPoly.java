// m4kb_world low poly: physics get cheaper for 20 seconds (jump boost and slow falling, a
// trail of cubes behind you).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Sounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;

final class M4kbLowPoly implements QuickEvent {
    private static final int DURATION = 20 * 20;

    @Override public String id() { return "m4kb_low_poly"; }

    @Override
    public void start(ServerPlayer player) {
        MarkerHunt.effect(player, MobEffects.JUMP, DURATION, 3);
        MarkerHunt.effect(player, MobEffects.SLOW_FALLING, DURATION, 0);
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 2.0F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        if (age % 4 == 0 && !player.onGround()) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.2, 0.1, 0.2, 0.0);
        }
        return age >= DURATION;
    }

    @Override
    public void stop(ServerPlayer player) {
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.SLOW_FALLING);
    }
}
