package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;
final class M4kbWorldTerrain extends CubeTerrain {
    private static final long SEED = 18295169L;
    private final BlockState[] states = {
            null,
            custom("grass_4kb"),
            custom("dirt_4kb"),
            custom("dirt_4kb"),
            custom("stone_4kb"),
            custom("bricks_4kb"),
            custom("log_4kb"),
            custom("leaves_4kb"),
            custom("stone_4kb"),
    };
    M4kbWorldTerrain() {
        super(build());
    }

    private static byte[] build() {
        byte[] cells = new byte[SIZE * SIZE * SIZE];
        Random rng = new Random(SEED);
        int[] ground = new int[SIZE * SIZE];
        Random offsets = new Random(SEED ^ 0xCAFEBABEL);
        for (int i = 0; i < ground.length; i++) ground[i] = offsets.nextInt(13) - 6;
        for (int i = 0; i < cells.length; i++) {
            int x = i % 64, y = (i / 64) % 64, z = i / 4096;
            int base = 20 + rng.nextInt(8);
            int threshold = base + (y < base + 8 ? ground[x + z * 64] : 0);
            cells[i] = (byte) (y > threshold ? rng.nextInt(8) + 1 : 0);
        }
        return cells;
    }

    @Override
    BlockState state(int type) {
        return states[type];
    }
}
