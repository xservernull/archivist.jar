package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

/**
 * neverforgetyou
 */
final class NeverforgetyouTerrain extends Terrain {
    private static final int MIN_Y = -32, BASE = 20, RANGE = 55, CAVES = 4, TABLE = 2048;
    private static final float R_MIN = 7.0f, R_SPREAD = 7.0f, V_FACTOR = 0.6f, DEPTH_MIN = 3.0f, DEPTH_SPREAD = 6.0f;
    private static final float[] SIN = new float[TABLE];

    static {
        for (int i = 0; i < TABLE; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0 / TABLE);
    }

    private final BlockState dogtooth = custom("dogtooth");
    private final BlockState deepslate = parse("minecraft:deepslate");

    @Override
    void fill(Canvas c) {
        int[] heights = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) heights[lz << 4 | lx] = height(c.x0 + lx, c.z0 + lz);
        }
        int n = CAVES * 9;
        float[] cx = new float[n], cy = new float[n], cz = new float[n], rh = new float[n], rv = new float[n];
        int idx = 0;
        for (int nx = -1; nx <= 1; nx++) {
            for (int nz = -1; nz <= 1; nz++) {
                int ncx = c.x0 + nx * 16, ncz = c.z0 + nz * 16;
                for (int k = 0; k < CAVES; k++) {
                    double s1 = frac(StrictMath.sin(ncx * 127.1 + ncz * 311.7 + k * 74.3) * 43758.5453);
                    double s2 = frac(StrictMath.sin(ncx * 269.5 + ncz * 183.3 + k * 19.9) * 43758.5453);
                    double s3 = frac(StrictMath.sin(ncx * 91.3 + ncz * 457.1 + k * 33.1) * 43758.5453);
                    double s4 = frac(StrictMath.sin(ncx * 53.1 + ncz * 211.7 + k * 99.1) * 43758.5453);
                    double s5 = frac(StrictMath.sin(ncx * 173.7 + ncz * 391.3 + k * 41.7) * 43758.5453);
                    float tcx = ncx + (float) (s1 * 16);
                    float tcz = ncz + (float) (s2 * 16);
                    float depth = DEPTH_MIN + (float) (s3 * DEPTH_SPREAD);
                    float trh = R_MIN + (float) (s4 * R_SPREAD);
                    cx[idx] = tcx;
                    cz[idx] = tcz;
                    cy[idx] = height((int) tcx, (int) tcz) - depth;
                    rh[idx] = trh;
                    rv[idx] = trh * V_FACTOR + (float) (s5 * 2.0f);
                    idx++;
                }
            }
        }
        int[] near = new int[n];
        float[] dxz = new float[n];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = c.x0 + lx, wz = c.z0 + lz;
                int count = 0;
                for (int k = 0; k < n; k++) {
                    float dx = wx - cx[k], dz = wz - cz[k];
                    float rh2 = rh[k] * rh[k];
                    float horizontal = dx * dx / rh2 + 0.0f; // keep the old float evaluation order below
                    float dzTerm = dz * dz / rh2;
                    if (horizontal + dzTerm < 1.0f) {
                        near[count] = k;
                        dxz[count] = horizontal; // dx term; dz term recomputed with the same operands
                        count++;
                    }
                }
                int top = heights[lz << 4 | lx];
                for (int y = MIN_Y; y <= top; y++) {
                    boolean carved = false;
                    for (int j = 0; j < count && !carved; j++) {
                        int k = near[j];
                        float dy = y - cy[k];
                        float rv2 = rv[k] * rv[k];
                        float dz = (c.z0 + lz) - cz[k];
                        float rh2 = rh[k] * rh[k];
                        carved = dxz[j] + dy * dy / rv2 + dz * dz / rh2 < 1.0f;
                    }
                    if (!carved) c.set(lx, y, lz, y < 0 ? deepslate : dogtooth);
                }
            }
        }
    }
    private static double frac(double d) {
        return d - Math.floor(d);
    }

    private static int height(int x, int z) {
        double h = 0;
        h += sincos(x, z, 0.012, 0.0, 0.0) * 0.40;
        h += sincos(x, z, 0.030, 1.3, 0.7) * 0.25;
        h += sincos(x, z, 0.060, 2.1, 3.3) * 0.20;
        h += sincos(x, z, 0.110, 4.7, 1.9) * 0.15;
        h = (h + 1.0) * 0.5;
        h = h * h * (3.0 - 2.0 * h);
        return MIN_Y + BASE + (int) (h * RANGE);
    }
    private static double sincos(int x, int z, double freq, double phaseX, double phaseZ) {
        int ix = Math.floorMod((int) ((x * freq + phaseX) * TABLE / (Math.PI * 2)), TABLE);
        int iz = Math.floorMod((int) ((z * freq + phaseZ) * TABLE / (Math.PI * 2)), TABLE);
        return SIN[ix] * SIN[iz];
    }
}
