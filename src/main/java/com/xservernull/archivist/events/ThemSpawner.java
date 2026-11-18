// the them waves of archivist:far_lands (old themspawner; in the old mod nothing ever called
// it, so they only came from /summon; this one runs).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Them;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

public final class ThemSpawner {
    private ThemSpawner() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceKey<Level> FAR_LANDS =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "far_lands"));

    private static final String KEY = "them_spawn", COUNT_KEY = "them_spawn_count";
    private static final int INITIAL_WAVES = 2;
    private static final int MIN_RANGE = 10, MAX_RANGE = 30;
    private static final double COUNT_RADIUS = 80.0;
    private static final int RETRY = 20;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    private static void tick(ServerPlayer player) {
        if (!player.level().dimension().equals(FAR_LANDS)) return;
        if (!player.isAlive() || player.isSpectator() || player.isCreative()) return;
        long now = player.level().getGameTime();
        int cd = ArchivistConfig.themCooldown();
        EventState.Memory memory = Events.memory(player);
        long next = memory.cooldownUntil(KEY);
        if (next == 0) {
            memory.setCooldown(KEY, now + cd);
            return;
        }
        if (now < next) return;

        ServerLevel level = (ServerLevel) player.level();
        long nearby = level.getEntitiesOfClass(Them.class, player.getBoundingBox().inflate(COUNT_RADIUS)).size();
        if (nearby >= ArchivistConfig.maxThem()) {
            memory.setCooldown(KEY, now + Math.max(RETRY, cd / 2));
            return;
        }
        if (!spawn(level, player)) {
            memory.setCooldown(KEY, now + RETRY);
            return;
        }
        long waves = memory.cooldownUntil(COUNT_KEY) + 1;
        memory.setCooldown(COUNT_KEY, waves);
        memory.setCooldown(KEY, now + (waves < INITIAL_WAVES ? cd : Math.max(RETRY, cd / 2)));
    }

    private static boolean spawn(ServerLevel level, ServerPlayer player) {
        var random = level.random;
        double angle = random.nextDouble() * 2 * Math.PI;
        int range = MIN_RANGE + random.nextInt(MAX_RANGE - MIN_RANGE);
        int x = player.blockPosition().getX() + (int) (Math.cos(angle) * range);
        int z = player.blockPosition().getZ() + (int) (Math.sin(angle) * range);
        if (!level.hasChunk(x >> 4, z >> 4)) return false;
        int y = standingY(level, x, player.blockPosition().getY(), z);
        if (y == Integer.MIN_VALUE) return false;

        Them them = Mobs.THEM.get().create(level);
        if (them == null) return false;
        them.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360F, 0F);
        them.setTarget(player);
        level.addFreshEntity(them);
        LOG.info("[them] spawned at {} {} {} for {}", x, y, z, player.getGameProfile().getName());
        return true;
    }

    private static int standingY(ServerLevel level, int x, int fromY, int z) {
        for (int d = 0; d <= 8; d++) {
            for (int sign = -1; sign <= 1; sign += 2) {
                if (d == 0 && sign == 1) continue;
                int y = fromY + sign * d;
                if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 2) continue;
                BlockPos feet = new BlockPos(x, y, z);
                if (level.getBlockState(feet.below()).isSolid()
                        && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                        && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                        && level.getFluidState(feet).isEmpty()) {
                    return y;
                }
            }
        }
        return Integer.MIN_VALUE;
    }
}
