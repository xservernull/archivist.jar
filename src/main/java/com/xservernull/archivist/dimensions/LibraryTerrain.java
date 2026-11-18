package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

/**
 * library
 */
final class LibraryTerrain extends Terrain {
    private final Tile tile = Tile.load("library");

    @Override
    void fill(Canvas c) {
        int plane = tile.sx * tile.sz;
        for (int lz = 0; lz < 16; lz++) {
            int zm = Math.floorMod(c.z0 + lz, tile.sz) * tile.sx;
            for (int lx = 0; lx < 16; lx++) {
                int base = zm + Math.floorMod(c.x0 + lx, tile.sx);
                for (int y = c.minY; y < c.maxY; y++) {
                    BlockState s = tile.cells[Math.floorMod(y, tile.sy) * plane + base];
                    if (s != null) c.set(lx, y, lz, s);
                }
            }
        }
    }
}
