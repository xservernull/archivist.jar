package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Arrays;

/** one chunk's blocks.*/
abstract class Canvas {
    final int x0, z0, minY, maxY;
    final int[] topAny = new int[256];
    final int[] topSolid = new int[256];
    final BlockState[] stateAny = new BlockState[256];
    final BlockState[] stateSolid = new BlockState[256];

    Canvas(int x0, int z0, int minY, int maxY) {
        this.x0 = x0;
        this.z0 = z0;
        this.minY = minY;
        this.maxY = maxY;
        Arrays.fill(topAny, Integer.MIN_VALUE);
        Arrays.fill(topSolid, Integer.MIN_VALUE);
    }

    final void set(int lx, int y, int lz, BlockState s) {
        if (y < minY || y >= maxY || s.isAir()) return;
        put(lx, y, lz, s);
        int i = lz << 4 | lx;
        if (y >= topAny[i]) {
            topAny[i] = y;
            stateAny[i] = s;
        }
        if (y >= topSolid[i] && s.blocksMotion()) {
            topSolid[i] = y;
            stateSolid[i] = s;
        }
    }
    final void run(int lx, int lz, int y0, int y1, BlockState s) {
        for (int y = Math.max(y0, minY); y <= y1 && y < maxY; y++) set(lx, y, lz, s);
    }
    abstract void put(int lx, int y, int lz, BlockState s);
    abstract BlockState get(int lx, int y, int lz);
}
