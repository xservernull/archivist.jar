// spawns and handles the message orbs that show up after the tenth orb, placed with a seeded
// rng per player

package com.xservernull.archivist.events.neverforgetyou;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.GlowOrb;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;
import java.util.UUID;

final class NfyMessageOrbs {
    private NfyMessageOrbs() {}

    private static final double MIN_RADIUS = 60.0;
    private static final double MAX_RADIUS = 260.0;

    static final int CHECK_INTERVAL = 20;

    static void start(ServerPlayer player, NfyState.Dialogue dialogue) {
        dialogue.messageOrbsOn = true;
        dialogue.messageSpawned = 0;
        dialogue.messageX = player.getX();
        dialogue.messageZ = player.getZ();
    }

    static double[] position(UUID owner, NfyState.Dialogue dialogue, int index) {
        Random random = new Random(owner.getMostSignificantBits() ^ owner.getLeastSignificantBits());
        double x = 0, z = 0;
        for (int i = 0; i <= index; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            x = dialogue.messageX + Math.cos(angle) * radius;
            z = dialogue.messageZ + Math.sin(angle) * radius;
        }
        return new double[] {x, z};
    }

    static void spawnDue(ServerPlayer player, ServerLevel level, NfyState.Dialogue dialogue, NfyScript script) {
        int count = Math.min(32, script.messageOrbs().size());
        for (int i = 0; i < count; i++) {
            if ((dialogue.messageSpawned & (1 << i)) != 0) continue;
            double[] at = position(player.getUUID(), dialogue, i);
            int bx = (int) Math.floor(at[0]);
            int bz = (int) Math.floor(at[1]);
            if (!Ground.isLoaded(level, bx, bz)) continue;
            double y = Ground.openY(level, bx, bz, player.getY()) + 1.5;
            level.addFreshEntity(GlowOrb.create(level, new Vec3(at[0], y, at[1]), GlowOrb.MESSAGE_BASE + i, player.getUUID()));
            dialogue.messageSpawned |= 1 << i;
        }
    }

    static void collected(ServerPlayer player, GlowOrb orb, NfyScript script) {
        List<String> lines = script.messageOrbs();
        int i = orb.index() - GlowOrb.MESSAGE_BASE;
        if (i < 0 || i >= lines.size()) return;
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.END_ROD, orb.getX(), orb.getY(), orb.getZ(), 12, 0.3, 0.3, 0.3, 0.03);
        level.playSound(null, orb.getX(), orb.getY(), orb.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.6F, 1.4F);
        NfyEvent.progress(player).collectMessage(i);
        NfyNet.read(player, lines.get(i));
    }
}
