package com.xservernull.archivist.dimensions;

import java.util.function.Supplier;

/**
 * All terrain for Realm: one small class per dimension, one each.
 */
public final class Terrains {
    private Terrains() {}
    public enum Kind {
        LIBRARY("library", 0, 256, -63, LibraryTerrain::new),
        ECHO_CHAMBER("echo_chamber", -64, 384, -63, EchoChamberTerrain::new),
        XOR_WORLD("xor_world", -64, 320, -64, XorWorldTerrain::new),
        M4KB_WORLD("m4kb_world", -64, 320, -64, M4kbWorldTerrain::new),
        RD132011("rd132011", 0, 128, 0, Rd132011Terrain::new),
        BEDROCK_FAR_LANDS("bedrock_far_lands", 0, 256, 64, BedrockFarLandsTerrain::new),
        FAR_LANDS("far_lands", 0, 256, 64, FarLandsTerrain::new),
        NEVERFORGETYOU("neverforgetyou", -64, 384, 0, NeverforgetyouTerrain::new),
        OLD_NETHER("old_nether", 0, 128, 31, OldNetherTerrain::new),
        INFDEV("infdev20100227", 0, 128, 64, InfdevTerrain::new),
        AETHER("aether", 0, 256, 0, AetherTerrain::new),
        MANSION("mansion_dim", 0, 256, 0, MansionTerrain::new),
        VILLLLAGE("villllage", 0, 256, 63, VilllageTerrain::new);

        public final String id;
        public final int minY, height, seaLevel;
        private final Supplier<Terrain> maker;
        private volatile Terrain terrain;

        Kind(String id, int minY, int height, int seaLevel, Supplier<Terrain> maker) {
            this.id = id;
            this.minY = minY;
            this.height = height;
            this.seaLevel = seaLevel;
            this.maker = maker;
        }

        public Terrain terrain() {
            Terrain t = terrain;
            if (t == null) {
                synchronized (this) {
                    t = terrain;
                    if (t == null) {
                        t = maker.get();
                        terrain = t;
                    }
                }
            }
            return t;
        }
        public static Kind of(String id) {
            for (Kind k : values()) if (k.id.equals(id)) return k;
            return null;
        }
        public static String names() {
            StringBuilder sb = new StringBuilder();
            for (Kind k : values()) sb.append(sb.length() == 0 ? "" : ", ").append(k.id);
            return sb.toString();
        }
    }
}
