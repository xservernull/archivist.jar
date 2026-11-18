// entities in the mod's own dimensions (every dimension in the archivist namespace): no
// natural entities.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Pigmen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.OptionalInt;
import java.util.Set;

final class DimensionMobs {
    private DimensionMobs() {}

    private static final Set<MobSpawnType> BLOCKED = Set.of(
            MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.STRUCTURE,
            MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY);

    private static final ResourceLocation VILLLLAGE = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "villllage");
    private static final int PIGMEN_MAX = 8, PIGMEN_RADIUS = 64, PIGMEN_EVERY = 40, SPOT_TRIES = 10;
    private static final double PIGMEN_MIN_DIST = 12.0, PIGMEN_MAX_DIST = 36.0;

    static void listen() {
        NeoForge.EVENT_BUS.addListener(DimensionMobs::onFinalize);
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tickPigmen(player);
        });
    }

    private static boolean ours(Level level) {
        return level.dimension().location().getNamespace().equals(Archivist.MOD_ID);
    }

    private static void onFinalize(FinalizeSpawnEvent e) {
        if (!BLOCKED.contains(e.getSpawnType())) return;
        if (ours(e.getLevel().getLevel())) e.setSpawnCancelled(true);
    }

    private static void tickPigmen(ServerPlayer player) {
        if (player.tickCount % PIGMEN_EVERY != 0 || !player.isAlive() || player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        if (!level.dimension().location().equals(VILLLLAGE)) return;
        int near = level.getEntitiesOfClass(Pigmen.class, player.getBoundingBox().inflate(PIGMEN_RADIUS)).size();
        if (near >= PIGMEN_MAX) return;
        for (int i = 0; i < SPOT_TRIES; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = PIGMEN_MIN_DIST + level.random.nextDouble() * (PIGMEN_MAX_DIST - PIGMEN_MIN_DIST);
            int x = Mth.floor(player.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
            if (!Ground.isLoaded(level, x, z)) continue;
            OptionalInt y = Ground.standY(level, x, player.getBlockY() + 4, z);
            if (y.isEmpty()) continue;
            Pigmen pigmen = Mobs.PIGMEN.get().create(level);
            if (pigmen == null) return;
            pigmen.moveTo(x + 0.5, y.getAsInt(), z + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(pigmen);
            return;
        }
    }
}