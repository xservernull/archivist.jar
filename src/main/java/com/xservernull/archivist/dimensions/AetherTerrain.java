package com.xservernull.archivist.dimensions;

import com.xservernull.archivist.dimensions.AetherLayout.Island;
import com.xservernull.archivist.dimensions.AetherLayout.Landmark;
import com.xservernull.archivist.dimensions.AetherLayout.Tree;
import net.minecraft.world.level.block.state.BlockState;

/**
 * aether: floating islands, lakes that spill off the edge, quartz trees with portal stone crowns, cloud banks
 * below, and the hand-built castle. Where everything is comes from AetherLayout (pure math, one hash per
 * island) this class only writes blocks.
 */
final class AetherTerrain extends Terrain {
    private static final int FALL = 50;
    private static final int TOWER_CLEAR = 40;
    private final BlockState stone = parse("minecraft:cobblestone");
    private final BlockState grass = custom("aether_grass");
    private final BlockState log = parse("minecraft:quartz_pillar[axis=y]");
    private final BlockState leaf = custom("aetherportalstone");
    private final BlockState cloud = custom("cloud_block");
    private final BlockState water = parse("minecraft:water");

    // the castle and the tower bring their own ground, whose grass is real
    private final Prefab castle = Prefab.load("aether_castle", 0, true);
    private final Prefab temple = Prefab.load(Landmark.TEMPLE.structure, 0, true);
    private final Prefab tower = Prefab.load(Landmark.TOWER.structure, 0, true);

    @Override
    void fill(Canvas c) {
        int cell = AetherLayout.CELL, icell = AetherLayout.ISLET_CELL;
        int i0 = Math.floorDiv(c.x0, cell), j0 = Math.floorDiv(c.z0, cell);
        int i1 = Math.floorDiv(c.x0 + 15, cell), j1 = Math.floorDiv(c.z0 + 15, cell);
        Island[] big = new Island[4];
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) big[(i - i0) * 2 + (j - j0)] = AetherLayout.island(i, j);
        }
        int k0 = Math.floorDiv(c.x0, icell), m0 = Math.floorDiv(c.z0, icell);
        int k1 = Math.floorDiv(c.x0 + 15, icell), m1 = Math.floorDiv(c.z0 + 15, icell);
        Island[] small = new Island[4];
        for (int k = k0; k <= k1; k++) {
            for (int m = m0; m <= m1; m++) {
                Island s = AetherLayout.islet(k * icell, m * icell);
                small[(k - k0) * 2 + (m - m0)] = s != null && nearTower(s) ? null : s;
            }
        }

        for (int lz = 0; lz < 16; lz++) {
            int z = c.z0 + lz;
            for (int lx = 0; lx < 16; lx++) {
                int x = c.x0 + lx;
                int thick = AetherLayout.cloud(x, z);
                if (thick > 0) c.run(lx, lz, AetherLayout.CLOUD_Y, AetherLayout.CLOUD_Y + thick - 1, cloud);
                if (AetherLayout.inCastle(x, z)) castleColumn(c, lx, lz, x, z);
                Island b = big[(Math.floorDiv(x, cell) - i0) * 2 + (Math.floorDiv(z, cell) - j0)];
                if (b != null) island(c, lx, lz, x, z, b, true);
                Island s = small[(Math.floorDiv(x, icell) - k0) * 2 + (Math.floorDiv(z, icell) - m0)];
                if (s != null) island(c, lx, lz, x, z, s, false);
            }
        }
    }

    private static boolean nearTower(Island islet) {
        int ci = Math.floorDiv(islet.cx, AetherLayout.CELL), cj = Math.floorDiv(islet.cz, AetherLayout.CELL);
        for (int i = ci - 1; i <= ci + 1; i++) {
            for (int j = cj - 1; j <= cj + 1; j++) {
                Island b = AetherLayout.island(i, j);
                if (b == null || b.landmark != Landmark.TOWER) continue;
                long dx = islet.cx - b.cx, dz = islet.cz - b.cz;
                if (dx * dx + dz * dz < (long) TOWER_CLEAR * TOWER_CLEAR) return true;
            }
        }
        return false;
    }

    private void castleColumn(Canvas c, int lx, int lz, int x, int z) {
        int px = x - AetherLayout.CASTLE_X, pz = z - AetherLayout.CASTLE_Z;
        for (int py = 0; py < castle.sy; py++) {
            BlockState s = castle.at(px, py, pz);
            if (s != null && !s.isAir()) c.set(lx, AetherLayout.CASTLE_Y + py, lz, s);
        }
    }


    private void island(Canvas c, int lx, int lz, int x, int z, Island is, boolean big) {
        double edge = is.edge(x, z);
        if (edge >= 1) return;
        int top = is.top(x, z, edge);
        int bottom = is.bottom(x, z, edge, top);

        if (big && is.lake(x, z)) {
            int surface = Math.min(is.lakeLevel, top);
            if (is.spills(x, z, edge)) {
                // no ground here the water runs off the rim as a thin fall
                c.run(lx, lz, Math.max(1, bottom - FALL), surface, water);
            } else {
                c.run(lx, lz, bottom, surface - 3, stone);
                c.run(lx, lz, surface - 2, surface, water);
            }
            return;
        }

        // a landmark's building cells of it that are explicit air dig the ground out
        Prefab p = null;
        int px = 0, pz = 0, oy = 0;
        if (big && is.landmarkIsland) {
            Prefab candidate = is.landmark == Landmark.TEMPLE ? temple : tower;
            px = x - is.landmarkX();
            pz = z - is.landmarkZ();
            if (px >= 0 && px < candidate.sx && pz >= 0 && pz < candidate.sz) {
                p = candidate;
                oy = is.plateauTop() - 1;
            }
        }
        for (int y = bottom; y <= top; y++) {
            if (p != null) {
                int py = y - oy;
                if (py >= 0 && py < p.sy && p.carves(px, py, pz)) continue;
            }
            c.set(lx, y, lz, y == top ? grass : stone);
        }
        if (p != null) {
            for (int py = 0; py < p.sy; py++) {
                BlockState s = p.at(px, py, pz);
                if (s != null && !s.isAir()) c.set(lx, oy + py, lz, s);
            }
        }

        if (big) {
            Tree t = AetherLayout.tree(is, x, z);
            if (t != null) tree(c, lx, lz, t, x - t.x(), z - t.z());
        }
    }
    private void tree(Canvas c, int lx, int lz, Tree t, int dx, int dz) {
        int topLog = t.base() + t.height();
        for (int dy = -1; dy <= 1; dy++) {
            if (t.crown(dx, dy, dz)) c.set(lx, topLog + dy, lz, leaf);
        }
        if (dx == 0 && dz == 0) c.run(lx, lz, t.base() + 1, topLog, log);
    }
}
