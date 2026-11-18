// xor_world zero call: the middle of your cube hums.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.Texts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

final class XorZeroCall implements QuickEvent {
    private static final int SIZE = 64, PERIOD = 88, DURATION = 20 * 45;
    private static final double MIDDLE = SIZE / 2.0, ARRIVE = 3.0;

    @Override public String id() { return "xor_zero_call"; }

    private static double centre(double v) {
        return Math.floor(v / PERIOD) * PERIOD + MIDDLE;
    }

    @Override
    public void start(ServerPlayer player) {
        player.sendSystemMessage(Texts.tr("archivist.event.xor_zero_call.start", "§7something hums at the middle of the cube. a ^ a."));
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 0.6F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        double cx = centre(player.getX()), cz = centre(player.getZ());
        double dist = Math.hypot(player.getX() - cx, player.getZ() - cz);
        if (dist < ARRIVE) {
            level.sendParticles(ParticleTypes.END_ROD, cx, player.getY() + 1.0, cz, 30, 0.5, 0.8, 0.5, 0.05);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 0.8F);
            Gifts.give(player, Gifts.XOR_NOTES);
            player.sendSystemMessage(Texts.tr("archivist.event.xor_zero_call.win", "§7zero."));
            return true;
        }
        if (age % 10 == 0) {
            level.sendParticles(player, ParticleTypes.END_ROD, true, cx, player.getY() + 4.0, cz, 10, 0.1, 6.0, 0.1, 0.0);
        }
        if (age % 20 == 0) player.displayClientMessage(Component.literal("§7" + Math.round(dist) + "m"), true);
        return age >= DURATION;
    }
}
