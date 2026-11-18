package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** in-memory chunk*/
final class MemCanvas extends Canvas {
    private final BlockState[] data;

    MemCanvas(int x0, int z0, int minY, int maxY) {
        super(x0, z0, minY, maxY);
        this.data = new BlockState[(maxY - minY) << 8];
    }

    @Override
    void put(int lx, int y, int lz, BlockState s) {
        data[((y - minY) << 8) | lz << 4 | lx] = s;
    }

    @Override
    BlockState get(int lx, int y, int lz) {
        if (y < minY || y >= maxY) return Blocks.AIR.defaultBlockState();
        BlockState s = data[((y - minY) << 8) | lz << 4 | lx];
        return s != null ? s : Blocks.AIR.defaultBlockState();
    }
}
