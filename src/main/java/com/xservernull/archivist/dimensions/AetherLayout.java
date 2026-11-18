package com.xservernull.archivist.dimensions;

/**
 * Where everything in {@code archivist:aether} is
 */
final class AetherLayout {
    private AetherLayout() {}

    static final int CELL = 96;
    static final int ISLET_CELL = 40;
    static final int CLOUD_Y = 44;
    static final int TREE_CELL = 8;

    static final int CASTLE_X = -70, CASTLE_Y = 80, CASTLE_Z = -66, CASTLE_SX = 140, CASTLE_SY = 54, CASTLE_SZ = 132;
    static final int CASTLE_ROOM_X = 80, CASTLE_ROOM_Y = 10, CASTLE_ROOM_Z = 60;

    private static final double WARP = 0.15;
    private static final int PLATEAU = 27;       // radius of the flat centre of a landmark island (covers the 38x36 temple)
    private static final int CASTLE_MARGIN = 6;

    enum Landmark {
        NONE(null, 0, 0),
        TEMPLE("aethertemple", 38, 36),
        TOWER("aethertower", 36, 36);

        final String structure;
        final int sx, sz;

        Landmark(String structure, int sx, int sz) {
            this.structure = structure;
            this.sx = sx;
            this.sz = sz;
        }
    }

    static final class Island {
        final int cx, cz, radius, rim, dome, depth, seed;
        final boolean landmarkIsland;
        final Landmark landmark;
        final int lakeX, lakeZ, lakeR, lakeLevel;
        private final double flat;

        private Island(int cx, int cz, int radius, int rim, int dome, int depth, int seed,
                       Landmark landmark, double flat, int lakeX, int lakeZ, int lakeR) {
            this.cx = cx;
            this.cz = cz;
            this.radius = radius;
            this.rim = rim;
            this.dome = dome;
            this.depth = depth;
            this.seed = seed;
            this.landmark = landmark;
            this.landmarkIsland = landmark != Landmark.NONE;
            this.flat = flat;
            this.lakeX = lakeX;
            this.lakeZ = lakeZ;
            this.lakeR = lakeR;
            this.lakeLevel = lakeR == 0 ? 0 : rim + (int) Math.round(dome * (1 - 0.8 * 0.8));
        }

        double edge(int x, int z) {
            double dx = x - cx, dz = z - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            double n = noise(seed, x * 0.07, z * 0.07);
            double reach = landmarkIsland ? radius * (1 + WARP * (n + 1) * 0.5) : radius * (1 + WARP * n);
            if (dist <= flat) return 0;
            return (dist - flat) / (reach - flat);
        }

        boolean contains(int x, int z) {
            return edge(x, z) < 1;
        }
        int top(int x, int z, double edge) {
            int t = rim + (int) Math.round(dome * (1 - edge * edge));
            if (!landmarkIsland && radius > 10) t += (int) Math.round(noise(seed ^ 0x5bd1, x * 0.09, z * 0.09) * 1.2);
            return t;
        }
        int top(int x, int z) {
            return top(x, z, edge(x, z));
        }
        int bottom(int x, int z, double edge, int top) {
            int thick = 1 + (int) Math.round((depth - 1) * Math.pow(1 - edge * edge, 0.9));
            int crag = (int) (Math.max(0, noise(seed ^ 0x2f6b, x * 0.15, z * 0.15)) * 3 * (1 - edge));
            return Math.max(1, top - thick + 1 - crag);
        }

        boolean lake(int x, int z) {
            if (lakeR == 0) return false;
            int dx = x - lakeX, dz = z - lakeZ;
            return dx * dx + dz * dz < lakeR * lakeR;
        }


        boolean spills(int x, int z, double edge) {
            return lake(x, z) && edge > 0.86;
        }
        int landmarkX() {
            return cx - landmark.sx / 2;
        }
        int landmarkZ() {
            return cz - landmark.sz / 2;
        }
        int plateauTop() {
            return rim + dome;
        }
    }


    record Tree(int x, int z, int base, int height) {
        boolean crown(int dx, int dy, int dz) {
            int ax = Math.abs(dx), az = Math.abs(dz);
            return switch (dy) {
                case -1, 0 -> ax <= 2 && az <= 2 && !(ax == 2 && az == 2);
                case 1 -> ax + az <= 1;
                default -> false;
            };
        }
    }

    static int hash(int a, int b, int c) {
        int h = a * 0x2545F491 ^ b * 0x6C8E9CF5 ^ c * 0x1B873593;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h;
    }

    static double unit(int h) {
        return (h >>> 8) / (double) (1 << 24);
    }
    static double noise(int seed, double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        double sx = fx * fx * (3 - 2 * fx), sz = fz * fz * (3 - 2 * fz);
        double a = lattice(seed, x0, z0), b = lattice(seed, x0 + 1, z0);
        double c = lattice(seed, x0, z0 + 1), d = lattice(seed, x0 + 1, z0 + 1);
        double top = a + (b - a) * sx, bottom = c + (d - c) * sx;
        return top + (bottom - top) * sz;
    }

    private static double lattice(int seed, int x, int z) {
        return unit(hash(x, z, seed)) * 2 - 1;
    }

    // ---- castle ------------------------------------------------------------------------------------------------

    static boolean inCastle(int x, int z) {
        return x >= CASTLE_X && x < CASTLE_X + CASTLE_SX && z >= CASTLE_Z && z < CASTLE_Z + CASTLE_SZ;
    }

    private static boolean reserved(int i, int j) {
        int x0 = i * CELL, z0 = j * CELL;
        return x0 + CELL > CASTLE_X - CASTLE_MARGIN && x0 < CASTLE_X + CASTLE_SX + CASTLE_MARGIN
                && z0 + CELL > CASTLE_Z - CASTLE_MARGIN && z0 < CASTLE_Z + CASTLE_SZ + CASTLE_MARGIN;
    }

    // ---- islands -----------------------------------------------------------------------------------------------
    static Island islandAt(int x, int z) {
        return island(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL));
    }
    static Island island(int i, int j) {
        if (reserved(i, j)) return null;
        if (unit(hash(i, j, 1)) > 0.8) return null;
        Landmark lm = Landmark.NONE;
        if (unit(hash(i, j, 2)) < 0.2) lm = unit(hash(i, j, 3)) < 0.35 ? Landmark.TEMPLE : Landmark.TOWER;
        int radius = lm == Landmark.NONE ? 12 + (int) (unit(hash(i, j, 4)) * 15) : 30 + (int) (unit(hash(i, j, 4)) * 3);
        int margin = (int) Math.ceil(radius * (1 + WARP)) + 1;
        int span = CELL - 2 * margin + 1;
        int cx = i * CELL + margin + (int) (unit(hash(i, j, 5)) * span);
        int cz = j * CELL + margin + (int) (unit(hash(i, j, 6)) * span);
        int rim = 78 + (int) (unit(hash(i, j, 7)) * 34);
        int dome = lm == Landmark.NONE ? 3 + radius / 5 : 2;
        int depth = 6 + radius * 3 / 4;
        int seed = hash(i, j, 8);
        int lakeX = 0, lakeZ = 0, lakeR = 0;
        if (lm == Landmark.NONE && radius >= 16 && unit(hash(i, j, 9)) < 0.5) {
            double a = unit(hash(i, j, 10)) * Math.PI * 2;
            lakeR = 5 + radius / 5;
            lakeX = cx + (int) Math.round(Math.cos(a) * radius * 0.62);
            lakeZ = cz + (int) Math.round(Math.sin(a) * radius * 0.62);
        }
        return new Island(cx, cz, radius, rim, dome, depth, seed, lm, lm == Landmark.NONE ? 0 : PLATEAU, lakeX, lakeZ, lakeR);
    }
    static Island islet(int x, int z) {
        int i = Math.floorDiv(x, ISLET_CELL), j = Math.floorDiv(z, ISLET_CELL);
        if (unit(hash(i, j, 21)) > 0.55) return null;
        int radius = 5 + (int) (unit(hash(i, j, 22)) * 5);
        int margin = (int) Math.ceil(radius * (1 + WARP)) + 1;
        int span = ISLET_CELL - 2 * margin + 1;
        int cx = i * ISLET_CELL + margin + (int) (unit(hash(i, j, 23)) * span);
        int cz = j * ISLET_CELL + margin + (int) (unit(hash(i, j, 24)) * span);
        int rim = 150 + (int) (unit(hash(i, j, 25)) * 36);
        return new Island(cx, cz, radius, rim, 1, 3 + radius, hash(i, j, 26), Landmark.NONE, 0, 0, 0, 0);
    }

    // ---- trees -------------------------------------------------------------------------------------------------

    static Tree tree(Island is, int x, int z) {
        int tx = Math.floorDiv(x, TREE_CELL), tz = Math.floorDiv(z, TREE_CELL);
        int h = hash(tx, tz, is.seed);
        if (unit(h) > 0.45) return null;
        int px = tx * TREE_CELL + 2 + (h >>> 8 & 3);
        int pz = tz * TREE_CELL + 2 + (h >>> 10 & 3);
        double e = is.edge(px, pz);
        if (e > 0.8 || is.lake(px, pz)) return null;
        if (is.landmarkIsland && Math.max(Math.abs(px - is.cx), Math.abs(pz - is.cz)) <= 22) return null;
        return new Tree(px, pz, is.top(px, pz, e), 4 + (h >>> 12 & 3) % 3);
    }

    // ---- clouds ------------------------------------------------------------------------------------------------

    static int cloud(int x, int z) {
        double v = (noise(0x31c7, x * 0.012, z * 0.012) + 1) * 0.5;
        if (v < 0.55) return 0;
        return Math.min(3, 1 + (int) ((v - 0.55) / 0.45 * 3));
    }
}