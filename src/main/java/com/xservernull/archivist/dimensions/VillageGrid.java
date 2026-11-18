package com.xservernull.archivist.dimensions;

/**
 * the village layout as pure integer math
 */
final class VillageGrid {
    private VillageGrid() {}

    static final int CELL = 40;
    static final int MARGIN = 5;
    static final int USABLE = CELL - 2 * MARGIN;
    static final double STRUCTURE_CHANCE = 0.7;
    static final double EDGE_CHANCE = 0.55;
    private static final int SALT_EDGE = 0x0ED6E;
    private static final int SALT_SPAWN = 0x05BA0;
    private static final int SALT_FORCE = 0x0F0CE;
    private static final int SALT_LAMP = 0x01A4B;

    record Slot(int x, int z, int dx, int dz, int roll) {}

    private static double unit(int h) {
        return h / 2147483648.0;
    }


    static int roll(int i, int j, int salt) {
        return Terrain.hash(i, j, 0, salt);
    }

    static boolean hasStructure(int i, int j) {
        return unit(Terrain.hash(i, j, 0, SALT_SPAWN)) < STRUCTURE_CHANCE;
    }

    private static boolean baseEdge(int axis, int i, int j) {
        return unit(Terrain.hash(i, j, axis, SALT_EDGE)) < EDGE_CHANCE;
    }

    private static int forced(int i, int j) {
        if (!hasStructure(i, j)) return -1;
        if (baseEdge(0, i, j) || baseEdge(0, i, j + 1) || baseEdge(1, i, j) || baseEdge(1, i + 1, j)) return -1;
        return Terrain.hash(i, j, 1, SALT_FORCE) & 3;
    }

    static boolean edge(int axis, int i, int j) {
        if (baseEdge(axis, i, j)) return true;
        if (axis == 0) return forced(i, j) == 0 || forced(i, j - 1) == 1;
        return forced(i, j) == 2 || forced(i - 1, j) == 3;
    }

    static boolean road(int x, int z) {
        int li = Math.floorDiv(x + CELL / 2, CELL);
        int lj = Math.floorDiv(z + CELL / 2, CELL);
        boolean ns = Math.abs(x - li * CELL) <= 1;
        boolean ew = Math.abs(z - lj * CELL) <= 1;
        if (ns && ew) {
            return edge(1, li, lj - 1) || edge(1, li, lj) || edge(0, li - 1, lj) || edge(0, li, lj);
        }
        if (ns) return edge(1, li, Math.floorDiv(z, CELL));
        if (ew) return edge(0, Math.floorDiv(x, CELL), lj);
        return false;
    }

    static Slot[] lamps(int axis, int i, int j) {
        if (!edge(axis, i, j)) return new Slot[0];
        Slot[] out = new Slot[2];
        for (int k = 0; k < 2; k++) {
            int along = 10 + 20 * k;
            int h = Terrain.hash(i, j, axis * 2 + k, SALT_LAMP);
            int side = (h & 1) == 0 ? 1 : -1;
            int roll = h >>> 1;
            out[k] = axis == 1
                    ? new Slot(CELL * i, CELL * j + along, side, 0, roll)
                    : new Slot(CELL * i + along, CELL * j, 0, side, roll);
        }
        return out;
    }
}
