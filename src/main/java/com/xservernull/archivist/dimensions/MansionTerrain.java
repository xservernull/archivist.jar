package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

/**
 * mansion_dim
 */
final class MansionTerrain extends Terrain {
    private static final int Y_OFFSET = 64, REPEATS = 4;
    private final Tile tile = Tile.load("mansion");
    private final BlockState bedrock = parse("minecraft:bedrock");

    @Override
    void fill(Canvas c) {
        int stride = tile.sy - 2;
        for (int lz = 0; lz < 16; lz++) {
            int zm = Math.floorMod(c.z0 + lz, tile.sz) * tile.sx;
            for (int lx = 0; lx < 16; lx++) {
                int base = zm + Math.floorMod(c.x0 + lx, tile.sx);
                c.set(lx, Y_OFFSET - 1, lz, bedrock);
                for (int rep = 0; rep < REPEATS; rep++) {
                    int y0 = Y_OFFSET + rep * stride;
                    for (int y = 0; y < tile.sy; y++) {
                        BlockState s = tile.cells[y * tile.sx * tile.sz + base];
                        if (s != null) c.set(lx, y0 + y, lz, s);
                    }
                }
            }
        }
    }
}
