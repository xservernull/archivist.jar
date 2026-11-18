package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

/**
 * rd132011
 */
final class Rd132011Terrain extends Terrain {
    private static final int SOLID_TOP = 64 * 2 / 3; // 42
    private final BlockState cobble = parse("minecraft:cobblestone");
    private final BlockState grass = parse("minecraft:grass_block");
    private final BlockState terminal = parse("minecraft:command_block");

    @Override
    void fill(Canvas c) {
        int cx = c.x0 >> 4, cz = c.z0 >> 4;
        if (cx < 0 || cx >= 16 || cz < 0 || cz >= 16) return;
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                c.run(lx, lz, 0, SOLID_TOP - 1, cobble);
                c.set(lx, SOLID_TOP, lz, grass);
            }
        }
        if (cx == 8 && cz == 8) c.set(0, SOLID_TOP + 2, 0, terminal); // world 128, 44, 128
    }
}
