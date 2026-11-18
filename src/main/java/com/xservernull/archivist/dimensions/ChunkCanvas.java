package com.xservernull.archivist.dimensions;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/** writes straight into the chunk's sections*/
final class ChunkCanvas extends Canvas {
    private final ChunkAccess chunk;
    private final LevelChunkSection[] sections;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    ChunkCanvas(ChunkAccess chunk) {
        super(chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(),
                chunk.getMinBuildHeight(), chunk.getMaxBuildHeight());
        this.chunk = chunk;
        this.sections = chunk.getSections();
    }

    @Override
    void put(int lx, int y, int lz, BlockState s) {
        if (s.hasBlockEntity() || s.getLightEmission() != 0) {
            // block entities and light sources go through the normal path so the chunk registers them
            chunk.setBlockState(pos.set(x0 + lx, y, z0 + lz), s, false);
        } else {
            sections[(y - minY) >> 4].setBlockState(lx, y & 15, lz, s, false);
        }
    }

    @Override
    BlockState get(int lx, int y, int lz) {
        if (y < minY || y >= maxY) return Blocks.AIR.defaultBlockState();
        return sections[(y - minY) >> 4].getBlockState(lx, y & 15, lz);
    }
    void finish() {
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz << 4 | lx;
                if (topAny[i] != Integer.MIN_VALUE) surface.update(lx, topAny[i], lz, stateAny[i]);
                if (topSolid[i] != Integer.MIN_VALUE) floor.update(lx, topSolid[i], lz, stateSolid[i]);
            }
        }
    }
}
