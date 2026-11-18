package com.xservernull.archivist;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.OptionalInt;

/** The three ways the mod looks for the ground, in one place (server side, common code). */
public final class Ground {
    private Ground() {}
    public static boolean isLoaded(ServerLevel level, int x, int z) {
        return level.hasChunkAt(new BlockPos(x, 0, z));
    }
    public static double openY(ServerLevel level, int x, int z, double fallback) {
        if (!isLoaded(level, x, z)) return fallback;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int y = top; y <= top + 10; y++) {
            if (level.getBlockState(new BlockPos(x, y, z)).isAir()
                    && level.getBlockState(new BlockPos(x, y + 1, z)).isAir()) {
                return y;
            }
        }
        return top + 1.0;
    }
    public static OptionalInt standY(Level level, int x, int fromY, int z) {
        BlockPos ground = new BlockPos(x, fromY, z);
        while (ground.getY() > level.getMinBuildHeight() + 1 && !level.getBlockState(ground).isSolid()) {
            ground = ground.below();
        }
        if (level.getBlockState(ground.above()).isSolid() || level.getBlockState(ground.above(2)).isSolid()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(ground.getY() + 1);
    }

    public static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z);
    }
    private static final int SAFE_RADIUS = 8, SAFE_UP = 12, SAFE_DOWN = 24;


    public static boolean canStand(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() + 1 || feet.getY() >= level.getMaxBuildHeight() - 2) return false;
        BlockPos floor = feet.below();
        BlockState below = level.getBlockState(floor);
        var shape = below.getCollisionShape(level, floor);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) > 1.0 || !level.getFluidState(floor).isEmpty()) return false;
        if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CACTUS) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE)) return false;
        for (BlockPos pos : new BlockPos[] {feet, feet.above()}) {
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) return false;
            if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB)) return false;
        }
        return true;
    }
    public static java.util.Optional<BlockPos> safeSpot(ServerLevel level, int x, int y, int z) {
        for (int r = 0; r <= SAFE_RADIUS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int cx = x + dx, cz = z + dz;
                    level.getChunk(cx >> 4, cz >> 4);
                    for (int d = 0; d <= Math.max(SAFE_UP, SAFE_DOWN); d++) {
                        if (d <= SAFE_UP && canStand(level, new BlockPos(cx, y + d, cz))) return java.util.Optional.of(new BlockPos(cx, y + d, cz));
                        if (d > 0 && d <= SAFE_DOWN && canStand(level, new BlockPos(cx, y - d, cz))) return java.util.Optional.of(new BlockPos(cx, y - d, cz));
                    }
                }
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Last resort
     */
    public static BlockPos makePad(ServerLevel level, int x, int y, int z) {
        int feetY = Math.max(level.getMinBuildHeight() + 2, Math.min(level.getMaxBuildHeight() - 3, y));
        level.getChunk(x >> 4, z >> 4);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setBlockAndUpdate(new BlockPos(x + dx, feetY - 1, z + dz), Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x + dx, feetY, z + dz), Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(x + dx, feetY + 1, z + dz), Blocks.AIR.defaultBlockState());
            }
        }
        return new BlockPos(x, feetY, z);
    }
}
