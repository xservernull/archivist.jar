package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

final class XorWorldTerrain extends CubeTerrain {
    private static final long SEED = 18295169L;
    private final BlockState block = custom("xor_block");

    XorWorldTerrain() {
        super(build());
    }

    private static byte[] build() {
        byte[] cells = new byte[SIZE * SIZE * SIZE];
        Random rng = new Random(SEED);
        int[] ground = new int[SIZE * SIZE];
        Random offsets = new Random(SEED ^ 0xDEADBEEFL);
        for (int i = 0; i < ground.length; i++) ground[i] = offsets.nextInt(17) - 8;
        for (int i = 0; i < cells.length; i++) {
            int x = i % 64, y = (i / 64) % 64, z = i / 4096;
            int adjusted = Math.min(63, y * 2) + (y < 20 ? ground[x + z * 64] : 0);
            int roll = rng.nextInt(64);
            cells[i] = (byte) (roll < adjusted ? 1 : 0);
            if (rng.nextInt(100) == 0) cells[i] = 1;
        }
        return cells;
    }

    @Override
    BlockState state(int type) {
        return block;
    }
}
