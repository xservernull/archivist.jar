package com.xservernull.archivist.dimensions;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
the name lol
 */
final class BedrockFarLandsTerrain extends Terrain {
    private static final int SLAB_TOP = 68, SPACING = 8, HEIGHT = 256, THICK = 3;
    private static final int[][] PATH_RECTS = {{-48, -48, 48, 48}, {-120, -16, -64, 16}, {64, -100, 100, -20}, {-20, 60, 60, 120}};
    private static final int[][] COLUMNS = columns();
    private final BlockState barrier = parse("minecraft:barrier");
    private final BlockState dirt = parse("minecraft:dirt");
    private final BlockState grass = parse("minecraft:grass_block");
    private final BlockState fakeGrass = custom("fake_grass");

    private static int[][] columns() {
        Random rand = new Random(133713371337L);
        int[][] cols = new int[3][2];
        for (int i = 0; i < 3; i++) {
            cols[i][0] = rand.nextInt(96 * 2 / 3) * 3 - 96;
            cols[i][1] = rand.nextInt(96 * 2 / 3) * 3 - 96;
        }
        return cols;
    }

    @Override
    void fill(Canvas c) {
        for (int lz = 0; lz < 16; lz++) {
            int wz = c.z0 + lz;
            for (int lx = 0; lx < 16; lx++) {
                int wx = c.x0 + lx;
                c.set(lx, 63, lz, barrier);
                if (inColumn(wx, wz)) {
                    c.run(lx, lz, 64, HEIGHT - 1, dirt);
                    continue;
                }
                boolean solid = Math.floorMod(wx, 3) == 0 && Math.floorMod(wz, 3) == 0;
                for (int y = SLAB_TOP; y < HEIGHT; y += SPACING) {
                    if (isDirtPath(wx, y, wz) || isBranch(wx, y, wz)) c.set(lx, y, lz, dirt);
                    else if (solid) c.set(lx, y, lz, isFake(wx, y, wz) ? fakeGrass : grass);
                }
            }
        }
    }

    private static boolean inColumn(int x, int z) {
        for (int[] col : COLUMNS) {
            int dx = x - col[0], dz = z - col[1];
            if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) return true;
        }
        return false;
    }

    private static boolean isFake(int x, int y, int z) {
        long seed = (long) x * 341873128712L + (long) y * 998244353L + (long) z * 132897987541L;
        return firstDouble(seed) < 0.01;
    }

    private static boolean isDirtPath(int x, int y, int z) {
        int layer = (y - SLAB_TOP) / SPACING;
        int shift = (layer * 17) % 23;
        int sx = x - shift, sz = z + shift;
        for (int[] r : PATH_RECTS) {
            int minX = r[0], minZ = r[1], maxX = r[2], maxZ = r[3];
            if (sx < minX - THICK || sx > maxX + THICK) continue;
            if (sz < minZ - THICK || sz > maxZ + THICK) continue;
            boolean vEdge = (sx >= minX && sx <= minX + THICK) || (sx <= maxX && sx >= maxX - THICK);
            boolean hEdge = (sz >= minZ && sz <= minZ + THICK) || (sz <= maxZ && sz >= maxZ - THICK);
            if (vEdge || hEdge) return true;
        }
        return false;
    }

    private static boolean isBranch(int x, int y, int z) {
        int layer = (y - SLAB_TOP) / SPACING;
        for (int[] col : COLUMNS) {
            int dx = x - col[0], dz = z - col[1];
            if (Math.abs(dx) > 7 || Math.abs(dz) > 7) continue;
            boolean on = switch (layer % 4) {
                case 0 -> dz >= -2 && dz <= 2 && dx >= 1 && dx <= 7;
                case 1 -> dx >= -2 && dx <= 2 && dz >= 1 && dz <= 7;
                case 2 -> dz >= -2 && dz <= 2 && dx <= -1 && dx >= -7;
                default -> dx >= -2 && dx <= 2 && dz <= -1 && dz >= -7;
            };
            if (on) return true;
        }
        return false;
    }
}
