package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * infdev20100227
 */
final class InfdevTerrain extends Terrain {
    private static final int HEIGHT = 128, SEA = 64;

    private static final class Perlin {
        private final int[] perm = new int[512];
        private final double ox, oy, oz;

        Perlin(Random rng) {
            ox = rng.nextDouble() * 256.0;
            oy = rng.nextDouble() * 256.0;
            oz = rng.nextDouble() * 256.0;
            for (int i = 0; i < 256; i++) perm[i] = i;
            for (int i = 0; i < 256; i++) {
                int j = rng.nextInt(256 - i) + i;
                int tmp = perm[i];
                perm[i] = perm[j];
                perm[j] = tmp;
                perm[i + 256] = perm[i];
            }
        }
        double sample(double x, double y, double z) {
            double px = x + ox, py = y + oy, pz = z + oz;
            int xi = floor(px) & 0xFF, yi = floor(py) & 0xFF, zi = floor(pz) & 0xFF;
            double fx = px - floor(px), fy = py - floor(py), fz = pz - floor(pz);
            double u = fade(fx), v = fade(fy), w = fade(fz);
            int a = perm[xi] + yi, aa = perm[a] + zi, ab = perm[a + 1] + zi;
            int b = perm[xi + 1] + yi, ba = perm[b] + zi, bb = perm[b + 1] + zi;
            return lerp(w,
                    lerp(v, lerp(u, grad(perm[aa], fx, fy, fz), grad(perm[ba], fx - 1.0, fy, fz)),
                            lerp(u, grad(perm[ab], fx, fy - 1.0, fz), grad(perm[bb], fx - 1.0, fy - 1.0, fz))),
                    lerp(v, lerp(u, grad(perm[aa + 1], fx, fy, fz - 1.0), grad(perm[ba + 1], fx - 1.0, fy, fz - 1.0)),
                            lerp(u, grad(perm[ab + 1], fx, fy - 1.0, fz - 1.0), grad(perm[bb + 1], fx - 1.0, fy - 1.0, fz - 1.0))));
        }
        private static double fade(double t) { return t * t * t * (t * (t * 6.0 - 15.0) + 10.0); }
        private static double grad(int hash, double x, double y, double z) {
            int h = hash & 0xF;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
        private static int floor(double d) {
            int i = (int) d;
            return d < i ? i - 1 : i;
        }
    }
    private static final class Octaves {
        private final Perlin[] octaves;
        Octaves(Random rng, int count) {
            octaves = new Perlin[count];
            for (int i = 0; i < count; i++) octaves[i] = new Perlin(rng);
        }
        double sample(double x, double z) {
            double sum = 0.0, scale = 1.0;
            for (Perlin p : octaves) {
                sum += p.sample(x / scale, 0.0, z / scale) * scale;
                scale *= 2.0;
            }
            return sum;
        }
    }
    private final Octaves noiseB, noiseC, noiseD, noiseE, noiseF, noiseG;
    private final BlockState stone = parse("minecraft:stone");
    private final BlockState dirt = parse("minecraft:dirt");
    private final BlockState grass = parse("minecraft:grass_block");
    private final BlockState water = parse("minecraft:water");
    private final BlockState dandelion = parse("minecraft:dandelion");
    private final BlockState obsidian = parse("minecraft:obsidian");
    private final BlockState bricks = parse("minecraft:stone_bricks");
    InfdevTerrain() {
        Random seeder = new Random(0L);
        noiseB = new Octaves(seeder, 16);
        noiseC = new Octaves(seeder, 16);
        noiseD = new Octaves(seeder, 8);
        noiseE = new Octaves(seeder, 4);
        noiseF = new Octaves(seeder, 4);
        noiseG = new Octaves(seeder, 5);
    }
    int calcHeight(int k, int m) {
        float f1 = (float) (noiseB.sample(k / 0.03125, m / 0.03125) - noiseC.sample(k / 0.015625, m / 0.015625)) / 512.0f / 4.0f;
        float f2 = (float) noiseF.sample(k / 4.0, m / 4.0);
        float f3 = (float) noiseG.sample(k / 8.0, m / 8.0) / 8.0f;
        if (f2 > 0.0f) f2 = (float) (noiseD.sample(k * 0.25714284f * 2.0f, m * 0.25714284f * 2.0f) * f3 / 4.0);
        else f2 = (float) (noiseE.sample(k * 0.25714284f, m * 0.25714284f) * f3);
        int h = (int) (f1 + 64.0f + f2);
        if ((float) noiseF.sample(k, m) < 0.0f) {
            h = (h / 2) << 1;
            if ((float) noiseF.sample(k / 5.0, m / 5.0) < 0.0f) h++;
        }
        return h;
    }
    static int bricksHeight(int k, int m, int terrain) {
        int n = k / 1024, i1 = m / 1024; // truncating division on purpose, old code did that really bad
        Random rng = new Random(n + (long) i1 * 13871);
        int caveX = (n << 10) + 128 + rng.nextInt(512);
        int caveZ = (i1 << 10) + 128 + rng.nextInt(512);
        int h = 127 - Math.max(Math.abs(k - caveX), Math.abs(m - caveZ));
        if (h == 255) h = 1;
        return Math.max(h, terrain);
    }
    @Override
    void fill(Canvas c) {
        Random flowers = new Random((long) (c.x0 >> 4) * 341873128712L ^ (long) (c.z0 >> 4) * 132897987541L);
        for (int lx = 0; lx < 16; lx++) {
            int k = c.x0 + lx;
            for (int lz = 0; lz < 16; lz++) {
                int m = c.z0 + lz;
                int h = calcHeight(k, m);
                int bricksTop = bricksHeight(k, m, h);
                boolean axis = k == 0 || m == 0;
                for (int y = 0; y < HEIGHT; y++) {
                    BlockState s = null;
                    boolean fluid = false;
                    if (axis && y <= h + 2) s = obsidian;
                    else if (y == h && h >= SEA) s = grass;
                    else if (y <= h - 2) s = stone;
                    else if (y <= h) s = dirt;
                    else if (y <= SEA) {
                        s = water;
                        fluid = true;
                    }
                    if (y <= bricksTop && (s == null || fluid)) s = bricks;
                    if (s != null) c.set(lx, y, lz, s);
                }
                if (h >= SEA && flowers.nextDouble() < 0.02 && h + 1 < HEIGHT
                        && c.get(lx, h, lz) == grass && c.get(lx, h + 1, lz).isAir()) {
                    c.set(lx, h + 1, lz, dandelion);
                }
            }
        }
    }
}
