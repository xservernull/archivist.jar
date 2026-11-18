package com.xservernull.archivist.dimensions;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;

/**
 * old_nether
 */
final class OldNetherTerrain extends Terrain {
    private static final int HEIGHT = 128, LAVA = 31;
    private static final double SCALE = 0.03125;
    private final ImprovedNoise n1, n2, n3;
    private final BlockState bedrock = parse("minecraft:bedrock");
    private final BlockState lava = parse("minecraft:lava");
    private final BlockState gold = custom("old_nether_gold_ore");
    private final BlockState quartz = custom("old_nether_quartz_ore");
    private final BlockState nylium = custom("old_crimson_nylium");
    private final BlockState rack = custom("old_netherrack");

    OldNetherTerrain() {
        RandomSource random = RandomSource.create(42);
        n1 = new ImprovedNoise(RandomSource.create(random.nextLong()));
        n2 = new ImprovedNoise(RandomSource.create(random.nextLong()));
        n3 = new ImprovedNoise(RandomSource.create(random.nextLong()));
    }
    private double noise(int x, int y, int z) {
        return n1.noise(x * SCALE, y * SCALE, z * SCALE) * 0.5
                + n2.noise(x * SCALE * 2, y * SCALE * 2, z * SCALE * 2) * 0.25
                + n3.noise(x * SCALE * 0.5, y * SCALE * 0.5, z * SCALE * 0.5) * 0.25;
    }
    private static double yBias(int y) {
        double f = (double) y / HEIGHT;
        return -4.0 * (f - 0.5) * (f - 0.5) + 0.5;
    }
    @Override
    void fill(Canvas c) {
        final int levels = HEIGHT / 8 + 1;
        double[] nodes = new double[25 * levels];
        for (int nz = 0; nz < 5; nz++) {
            for (int nx = 0; nx < 5; nx++) {
                for (int ny = 0; ny < levels; ny++) {
                    nodes[(nz * 5 + nx) * levels + ny] = noise(c.x0 + nx * 4, ny * 8, c.z0 + nz * 4);
                }
            }
        }
        double[] col = new double[levels];
        for (int lz = 0; lz < 16; lz++) {
            int wz = c.z0 + lz;
            double fz = (lz & 3) / 4.0;
            for (int lx = 0; lx < 16; lx++) {
                int wx = c.x0 + lx;
                double fx = (lx & 3) / 4.0;
                int a = ((lz >> 2) * 5 + (lx >> 2)) * levels;
                for (int ny = 0; ny < levels; ny++) {
                    col[ny] = lerp(fz, lerp(fx, nodes[a + ny], nodes[a + levels + ny]),
                            lerp(fx, nodes[a + 5 * levels + ny], nodes[a + 6 * levels + ny]));
                }
                for (int y = 0; y < HEIGHT; y++) {
                    if (y <= 0 || y >= HEIGHT - 1) {
                        c.set(lx, y, lz, bedrock);
                    } else if ((y <= 4 || y >= 123) && hash(wx, y, wz, 1) % 5 > y % 5) {
                        c.set(lx, y, lz, bedrock);
                    } else if (lerp((y & 7) / 8.0, col[y >> 3], col[(y >> 3) + 1]) + yBias(y) > 0) {
                        int roll = hash(wx, y, wz, 2) % 120;
                        BlockState s = rack;
                        if (roll == 0) s = gold;
                        else if (roll == 1) s = quartz;
                        else if (roll == 2 && y > 60) s = nylium;
                        c.set(lx, y, lz, s);
                    } else if (y <= LAVA) {
                        c.set(lx, y, lz, lava);
                    }
                }
            }
        }
    }
}
