package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * far_lands:
 */
final class FarLandsTerrain extends Terrain {
    private static final int SEA = 64, TOP = 230, H = 256, LEVELS = H / 4 + 1;
    private static final int AIR = 0, WATER = 1, STONE = 2, IRON = 3, COAL = 4, BEDROCK = 5, GRASS = 6, DIRT = 7;
    private final BlockState[] states = new BlockState[8];
    private final BlockState log = parse("minecraft:oak_log[axis=y]");
    private final BlockState leaves = parse("minecraft:oak_leaves[distance=1,persistent=false]");

    FarLandsTerrain() {
        states[WATER] = parse("minecraft:water");
        states[STONE] = parse("minecraft:stone");
        states[IRON] = parse("minecraft:iron_ore");
        states[COAL] = parse("minecraft:coal_ore");
        states[BEDROCK] = parse("minecraft:bedrock");
        states[GRASS] = parse("minecraft:grass_block");
        states[DIRT] = parse("minecraft:dirt");
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static double grad(int h, double x, double y, double z) {
        h &= 15;
        double u = h < 8 ? x : y, v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static int ph(long x, long y, long z, long s) {
        long h = s ^ (x * 1664525L + 1013904223L) ^ (y * 22695477L + 1L) ^ (z * 6364136223846793005L + 1442695040888963407L);
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33; h *= 0xc4ceb9fe1a85ec53L; h ^= h >>> 33;
        return (int) (h & 0xFF);
    }

    private static double p3(double x, double y, double z, long s) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double fx = x - Math.floor(x), fy = y - Math.floor(y), fz = z - Math.floor(z);
        double ux = fade(fx), uy = fade(fy), uz = fade(fz);
        return lerp(uz,
                lerp(uy, lerp(ux, grad(ph(ix, iy, iz, s), fx, fy, fz), grad(ph(ix + 1, iy, iz, s), fx - 1, fy, fz)),
                        lerp(ux, grad(ph(ix, iy + 1, iz, s), fx, fy - 1, fz), grad(ph(ix + 1, iy + 1, iz, s), fx - 1, fy - 1, fz))),
                lerp(uy, lerp(ux, grad(ph(ix, iy, iz + 1, s), fx, fy, fz - 1), grad(ph(ix + 1, iy, iz + 1, s), fx - 1, fy, fz - 1)),
                        lerp(ux, grad(ph(ix, iy + 1, iz + 1, s), fx, fy - 1, fz - 1), grad(ph(ix + 1, iy + 1, iz + 1, s), fx - 1, fy - 1, fz - 1))));
    }

    private static double fbm(double x, double y, double z, long s, int oct, double lac, double gain) {
        double v = 0, a = 1, f = 1, m = 0;
        for (int i = 0; i < oct; i++) {
            v += p3(x * f, y * f, z * f, s + i * 99991L) * a;
            m += a;
            a *= gain;
            f *= lac;
        }
        return v / m;
    }
    static double wall(int x, int z) {
        return (fbm(x * 0.003, 0, z * 0.003, 0xDEADBEEFL, 5, 2.2, 0.55) + fbm(z * 0.003, 0, x * 0.003, 0xBEEFDEADL, 5, 2.2, 0.55)) * 0.5;
    }
    static double detail(int x, int y, int z) {
        double face = fbm(x * 0.04, y * 0.04, z * 0.04, 0xABCDEF12L, 2, 2.0, 0.4) * 0.15;
        double voidN = fbm(x * 0.015, y * 0.010, z * 0.015, 0xCAFEBABEL, 3, 2.0, 0.5);
        double voidCut = voidN > 0.25 ? (voidN - 0.25) * 4.0 : 0.0;
        double chaos = fbm(x * 0.008, y * 0.012, z * 0.008, 0x13572468L, 4, 2.0, 0.5) * 0.4;
        return face - voidCut + chaos;
    }
    private static double yTerm(int y) {
        double d = y <= SEA ? 2.5 : 0.0;
        if (y > TOP) d -= (y - TOP) / 20.0 * 3.0;
        return d;
    }
    private static int ore(int x, int y, int z) {
        long h = x * 1664525L ^ y * 22695477L ^ z * 6364136223846793005L ^ 0xDEADF00DL;
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        int r = (int) (h & 0xFFFF);
        if (y >= 1 && y <= 64 && r < 460) return IRON;
        if (y >= 1 && y <= 128 && r < 655) return COAL;
        return STONE;
    }
    @Override
    void fill(Canvas c) {
        double[] nodes = new double[25 * LEVELS];
        for (int nz = 0; nz < 5; nz++) {
            for (int nx = 0; nx < 5; nx++) {
                for (int ny = 0; ny < LEVELS; ny++) {
                    nodes[(nz * 5 + nx) * LEVELS + ny] = detail(c.x0 + nx * 4, ny * 4, c.z0 + nz * 4);
                }
            }
        }
        double[] col = new double[LEVELS];
        int[] code = new int[H];
        for (int lz = 0; lz < 16; lz++) {
            int wz = c.z0 + lz;
            double fz = (lz & 3) / 4.0;
            for (int lx = 0; lx < 16; lx++) {
                int wx = c.x0 + lx;
                double fx = (lx & 3) / 4.0;
                int a = ((lz >> 2) * 5 + (lx >> 2)) * LEVELS;
                for (int ny = 0; ny < LEVELS; ny++) {
                    col[ny] = lerp(fz, lerp(fx, nodes[a + ny], nodes[a + LEVELS + ny]),
                            lerp(fx, nodes[a + 5 * LEVELS + ny], nodes[a + 6 * LEVELS + ny]));
                }
                double wall = wall(wx, wz);
                code[0] = BEDROCK;
                for (int y = 1; y < H; y++) {
                    boolean solid;
                    if (y <= 4) solid = true;
                    else if (y > TOP + 20) solid = false;
                    else solid = wall + lerp((y & 3) / 4.0, col[y >> 2], col[(y >> 2) + 1]) + yTerm(y) > 0;
                    code[y] = solid ? ore(wx, y, wz) : (y < SEA ? WATER : AIR);
                }
                boolean hitAir = false;
                int dirt = 0;
                for (int y = H - 1; y >= 1; y--) {
                    int k = code[y];
                    if (k == AIR || k == WATER) {
                        hitAir = true;
                        dirt = 0;
                    } else if (k == STONE && hitAir) {
                        if (dirt == 0) code[y] = GRASS;
                        else if (dirt < 4) code[y] = DIRT;
                        else {
                            hitAir = false;
                            dirt = 0;
                            continue;
                        }
                        dirt++;
                    }
                }
                for (int y = 0; y < H; y++) {
                    if (code[y] != AIR) c.set(lx, y, lz, states[code[y]]);
                }
            }
        }
        trees(c);
    }
    private void trees(Canvas c) {
        Random r = new Random((long) (c.x0 >> 4) * 341873128712L ^ (long) (c.z0 >> 4) * 132897987541L ^ 0xF10A7L);
        int count = r.nextInt(3);
        for (int i = 0; i < count; i++) {
            int lx = 4 + r.nextInt(8), lz = 4 + r.nextInt(8);
            int y = 100 + r.nextInt(120);
            int trunk = 5 + r.nextInt(7);
            for (int k = 0; k < trunk; k++) put(c, lx, y + k, lz, log);
            int top = y + trunk;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dy = -2; dy <= 4; dy++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        if (dx * dx + dz * dz + dy * dy <= 17) put(c, lx + dx, top + dy, lz + dz, leaves);
                    }
                }
            }
        }
    }

    private static void put(Canvas c, int lx, int y, int lz, BlockState s) {
        BlockState now = c.get(lx, y, lz);
        if (now.isAir() || now.canBeReplaced()) c.set(lx, y, lz, s);
    }
}
