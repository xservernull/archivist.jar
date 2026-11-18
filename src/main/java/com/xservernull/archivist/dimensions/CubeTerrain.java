package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

/**
 * xor_world / m4kb_world
 */
abstract class CubeTerrain extends Terrain {
    static final int SIZE = 64, PERIOD = 88;
    final byte[] cube;

    CubeTerrain(byte[] cube) {
        this.cube = cube;
    }

    abstract BlockState state(int type);

    @Override
    void fill(Canvas c) {
        for (int lz = 0; lz < 16; lz++) {
            int tz = Math.floorMod(c.z0 + lz, PERIOD);
            if (tz >= SIZE) continue;
            for (int lx = 0; lx < 16; lx++) {
                int tx = Math.floorMod(c.x0 + lx, PERIOD);
                if (tx >= SIZE) continue;
                int base = tx + tz * 4096;
                for (int y = c.minY; y < c.maxY; y++) {
                    int ty = Math.floorMod(y, PERIOD);
                    if (ty >= SIZE) continue;
                    int type = cube[base + ty * 64];
                    if (type != 0) c.set(lx, y, lz, state(type));
                }
            }
        }
    }
}
