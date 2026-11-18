// xor_world reflection: a copy of you shows up on the opposite side of your cube (x and z
// flipped through the middle of the 64 block cell, 63 - x style) and moves backwards as you

package com.xservernull.archivist.events;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.entity.CopyMob;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalInt;

final class XorMirror implements QuickEvent {
    private static final String TAG = "archivist_xor_mirror";
    private static final int SIZE = 64, PERIOD = 88, DURATION = 20 * 40;
    private static final double MIDDLE = SIZE / 2.0, MEET = 2.0, MEET_HEIGHT = 6.0;

    @Override public String id() { return "xor_mirror"; }

    @Override
    public void start(ServerPlayer player) {
        sweep(player);
        ServerLevel level = player.serverLevel();
        Vec3 at = mirror(player.position());
        CopyMob twin = Mobs.COPY_FRIENDLY.get().create(level);
        if (at == null || twin == null) return;
        OptionalInt y = floor(level, at.x, at.z, player.getBlockY());
        if (y.isEmpty()) return;
        twin.setTargetPlayer(player);
        twin.setNoAi(true);
        twin.setInvulnerable(true);
        twin.setSilent(true);
        twin.addTag(TAG);
        twin.setPos(at.x, y.getAsInt(), at.z);
        level.addFreshEntity(twin);
        level.playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.5F, 0.7F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        CopyMob twin = level.getEntitiesOfClass(CopyMob.class, player.getBoundingBox().inflate(100.0), c -> ours(c, player))
                .stream().findFirst().orElse(null);
        if (twin == null) return true;
        Vec3 at = mirror(player.position());
        if (at != null) {
            OptionalInt y = floor(level, at.x, at.z, player.getBlockY());
            if (y.isPresent()) {
                float yaw = player.getYRot() + 180.0F;
                twin.setPos(at.x, y.getAsInt(), at.z);
                twin.setYRot(yaw);
                twin.setYHeadRot(yaw);
                twin.setYBodyRot(yaw);
                twin.setXRot(player.getXRot());
            }
        }
        if (inMiddle(player.position()) && Math.abs(twin.getY() - player.getY()) < MEET_HEIGHT) {
            level.sendParticles(ParticleTypes.POOF, twin.getX(), twin.getY() + 1.0, twin.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
            level.playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
            player.sendSystemMessage(Component.literal("§7a ^ a = 0."));
            EventNet.fx(player, "infdev_arrive", 15);
            return true;
        }
        return age >= DURATION;
    }

    @Override
    public void stop(ServerPlayer player) {
        sweep(player);
    }

    private static boolean ours(CopyMob mob, ServerPlayer player) {
        return mob.getTags().contains(TAG) && player.getUUID().equals(mob.targetPlayer());
    }

    private static void sweep(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (CopyMob twin : level.getEntities(EntityTypeTest.forClass(CopyMob.class), c -> ours(c, player))) {
                twin.discard();
            }
        }
    }

    private static double local(double v) {
        return v - Math.floor(v / PERIOD) * PERIOD;
    }

    private static Vec3 mirror(Vec3 p) {
        double lx = local(p.x), lz = local(p.z);
        if (lx >= SIZE || lz >= SIZE) return null;
        return new Vec3(p.x - lx + SIZE - lx, p.y, p.z - lz + SIZE - lz);
    }

    private static boolean inMiddle(Vec3 p) {
        return Math.hypot(local(p.x) - MIDDLE, local(p.z) - MIDDLE) < MEET;
    }

    private static OptionalInt floor(ServerLevel level, double x, double z, int fromY) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!Ground.isLoaded(level, bx, bz)) return OptionalInt.empty();
        OptionalInt y = Ground.standY(level, bx, fromY + 2, bz);
        return y.isPresent() ? y : Ground.standY(level, bx, fromY + 8, bz);
    }
}
