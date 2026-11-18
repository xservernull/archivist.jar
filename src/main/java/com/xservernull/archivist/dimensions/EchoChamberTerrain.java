package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

/**
 * echo_chamber
 */
final class EchoChamberTerrain extends Terrain {
    private static final int PX = 6, PY = 6, PZ = 5;
    private final BlockState[] table = new BlockState[PX * PZ * PY]; // [(x*PZ + z)*PY + y]

    EchoChamberTerrain() {
        BlockState cobble = parse("minecraft:cobblestone");
        BlockState stair = parse("minecraft:cobblestone_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]");
        for (int lx = 0; lx < PX; lx++) {
            int stairLY = Math.floorMod(5 - lx, PY);
            for (int lz = 0; lz < PZ; lz++) {
                boolean wall = lz == 0 || lz == PZ - 1;
                for (int ly = 0; ly < PY; ly++) {
                    int diag = Math.floorMod(lx + ly, PY);
                    int headroom = Math.floorMod(ly - stairLY, PY);
                    BlockState s = null;
                    if (wall) {
                        boolean floor = diag == 4 || (diag == 3 && lx >= 2);
                        if (floor || headroom <= 3) s = cobble;
                    } else if (diag == 5) {
                        s = stair;
                    } else if (diag == 4 || (diag == 3 && lx >= 2)) {
                        s = cobble;
                    }
                    table[(lx * PZ + lz) * PY + ly] = s;
                }
            }
        }
    }

    @Override
    void fill(Canvas c) {
        for (int lz = 0; lz < 16; lz++) {
            int zm = Math.floorMod(c.z0 + lz, PZ);
            for (int lx = 0; lx < 16; lx++) {
                int base = (Math.floorMod(c.x0 + lx, PX) * PZ + zm) * PY;
                for (int y = c.minY; y < c.maxY; y++) {
                    BlockState s = table[base + Math.floorMod(y, PY)];
                    if (s != null) c.set(lx, y, lz, s);
                }
            }
        }
    }
}
