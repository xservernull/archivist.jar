// the copies in the overworld (port of the overworld branch of the old entityspawner).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.CopyMob;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;
import java.util.function.Supplier;

public final class CopySpawner {
    private CopySpawner() {}

    private static final String KEY = "overworld_copies";
    private static final int INTERVAL = 300, MAX_NEAR = 4, CHANCE_PERCENT = 35, CHECK_RADIUS = 96, TRIES = 15;
    private static final double MIN_DIST = 32.0, MAX_DIST = 48.0;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        if (!player.level().dimension().equals(Level.OVERWORLD)) return;
        if (!player.isAlive() || player.isSpectator()) return;
        long now = player.level().getGameTime();
        EventState.Memory memory = Events.memory(player);
        if (now < memory.cooldownUntil(KEY)) return;
        memory.setCooldown(KEY, now + INTERVAL);

        ServerLevel level = player.serverLevel();
        if (level.random.nextInt(100) >= CHANCE_PERCENT) return;
        int near = level.getEntitiesOfClass(CopyMob.class, player.getBoundingBox().inflate(CHECK_RADIUS)).size();
        if (near >= MAX_NEAR) return;

        BlockPos pos = findSpot(level, player.blockPosition());
        if (pos == null) return;
        Supplier<EntityType<CopyMob>> type = level.isDay()
                ? Mobs.COPY_FRIENDLY
                : List.of(Mobs.COPY_ZOMBIE, Mobs.COPY_ARROW, Mobs.COPY_COBWEB).get(level.random.nextInt(3));
        CopyMob copy = type.get().create(level);
        if (copy == null) return;
        copy.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        copy.setTargetPlayer(player);
        level.addFreshEntity(copy);
    }

    private static BlockPos findSpot(ServerLevel level, BlockPos center) {
        for (int i = 0; i < TRIES; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = MIN_DIST + level.random.nextDouble() * (MAX_DIST - MIN_DIST);
            int x = center.getX() + (int) (Math.cos(angle) * dist);
            int z = center.getZ() + (int) (Math.sin(angle) * dist);
            if (!Ground.isLoaded(level, x, z)) continue;
            BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (Ground.canStand(level, feet)) return feet;
        }
        return null;
    }
}
