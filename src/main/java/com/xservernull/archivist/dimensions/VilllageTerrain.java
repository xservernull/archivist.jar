package com.xservernull.archivist.dimensions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xservernull.archivist.Archivist;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * villllage: flat grass with a road grid, lampposts and one small building per cell
 */
final class VilllageTerrain extends Terrain {
    private static final int GROUND = 64;

    private record Entry(Prefab[] variants, int weight) {}

    /** pne prefab at a world position */
    private record Placed(Prefab p, int ox, int oy, int oz) {
        boolean covers(int x, int z) {
            return x >= ox && x < ox + p.sx && z >= oz && z < oz + p.sz;
        }
    }

    private final BlockState bedrock = parse("minecraft:bedrock");
    private final BlockState stone = parse("minecraft:stone");
    private final BlockState dirt = parse("minecraft:dirt");
    private final BlockState grass = parse("minecraft:grass_block");
    private final BlockState gravel = parse("minecraft:gravel");

    private final Entry[] structures;
    private final int weightTotal;
    private final Prefab[] lamps;

    VilllageTerrain() {
        JsonObject o = readJson("villllage");
        List<Entry> list = new ArrayList<>();
        int total = 0;
        for (JsonElement el : o.getAsJsonArray("structures")) {
            JsonObject e = el.getAsJsonObject();
            int weight = e.get("weight").getAsInt();
            int y = e.has("y") ? e.get("y").getAsInt() : 0;
            Prefab[] variants;
            if (e.has("builtin")) {
                String b = e.get("builtin").getAsString();
                variants = switch (b) {
                    case "oak_tree" -> Prefab.oaks();
                    case "spruce_tree" -> Prefab.spruces();
                    default -> throw new IllegalStateException("villllage.json: unknown builtin '" + b + "'");
                };
            } else {
                variants = new Prefab[]{Prefab.load(e.get("name").getAsString(), y)};
            }
            for (Prefab p : variants) {
                if (p.sx > VillageGrid.USABLE || p.sz > VillageGrid.USABLE) {
                    throw new IllegalStateException("villllage.json: " + p.name + " is " + p.sx + "x" + p.sz
                            + ", the cell only has room for " + VillageGrid.USABLE);
                }
            }
            list.add(new Entry(variants, weight));
            total += weight;
        }
        structures = list.toArray(new Entry[0]);
        weightTotal = total;
        JsonArray la = o.getAsJsonArray("lamps");
        lamps = new Prefab[la.size()];
        for (int i = 0; i < lamps.length; i++) {
            JsonObject e = la.get(i).getAsJsonObject();
            lamps[i] = Prefab.load(e.get("name").getAsString(), e.has("y") ? e.get("y").getAsInt() : 0);
            if (lamps[i].sx > 3 || lamps[i].sz > 3) {
                throw new IllegalStateException("villllage.json: lamppost " + lamps[i].name + " is wider than 3");
            }
        }
    }

    private static JsonObject readJson(String name) {
        String path = "/data/" + Archivist.MOD_ID + "/realm/" + name + ".json";
        try (InputStream in = VilllageTerrain.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    @Override
    void fill(Canvas c) {
        List<Placed> placed = collect(c.x0, c.z0);
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int wx = c.x0 + lx, wz = c.z0 + lz;
                Placed cover = null;
                for (Placed p : placed) {
                    if (p.covers(wx, wz)) {
                        cover = p;
                        break;
                    }
                }
                boolean road = VillageGrid.road(wx, wz);
                if (cover == null) {
                    c.set(lx, 0, lz, bedrock);
                    c.run(lx, lz, 1, GROUND - 4, stone);
                    c.run(lx, lz, GROUND - 3, GROUND - 2, dirt);
                    c.set(lx, GROUND - 1, lz, road ? gravel : dirt);
                    c.set(lx, GROUND, lz, road ? gravel : grass);
                } else {
                    column(c, lx, lz, cover, wx - cover.ox, wz - cover.oz);
                }
            }
        }
    }
    private void column(Canvas c, int lx, int lz, Placed cover, int px, int pz) {
        Prefab p = cover.p;
        c.set(lx, 0, lz, bedrock);
        for (int y = 1; y <= GROUND; y++) {
            int py = y - cover.oy;
            if (py >= 0 && py < p.sy && p.carves(px, py, pz)) continue;
            c.set(lx, y, lz, y == GROUND ? grass : y >= GROUND - 3 ? dirt : stone);
        }
        for (int py = 0; py < p.sy; py++) {
            BlockState s = p.at(px, py, pz);
            if (s != null && !s.isAir()) c.set(lx, cover.oy + py, lz, s);
        }
    }
    private List<Placed> collect(int x0, int z0) {
        List<Placed> out = new ArrayList<>(8);
        int cell = VillageGrid.CELL;
        int i0 = Math.floorDiv(x0 - 4, cell), i1 = Math.floorDiv(x0 + 19, cell);
        int j0 = Math.floorDiv(z0 - 4, cell), j1 = Math.floorDiv(z0 + 19, cell);
        for (int i = i0; i <= i1; i++) {
            for (int j = j0; j <= j1; j++) {
                if (VillageGrid.hasStructure(i, j)) out.add(structure(i, j));
                for (int axis = 0; axis < 2; axis++) {
                    for (VillageGrid.Slot s : VillageGrid.lamps(axis, i, j)) out.add(lamp(s));
                }
            }
        }
        return out;
    }

    private Placed structure(int i, int j) {
        int roll = VillageGrid.roll(i, j, 0x0D1CE) % weightTotal;
        Entry e = structures[structures.length - 1];
        for (Entry cand : structures) {
            if (roll < cand.weight) {
                e = cand;
                break;
            }
            roll -= cand.weight;
        }
        Prefab p = e.variants[VillageGrid.roll(i, j, 0x0FA57) % e.variants.length];
        int jx = VillageGrid.roll(i, j, 0x0AAA1) % (VillageGrid.USABLE - p.sx + 1);
        int jz = VillageGrid.roll(i, j, 0x0AAA2) % (VillageGrid.USABLE - p.sz + 1);
        return new Placed(p, i * VillageGrid.CELL + VillageGrid.MARGIN + jx, GROUND + 1 + p.yOffset,
                j * VillageGrid.CELL + VillageGrid.MARGIN + jz);
    }

    private Placed lamp(VillageGrid.Slot s) {
        Prefab p = lamps[s.roll() % lamps.length];
        int ox = s.dx() > 0 ? s.x() + 2 : s.dx() < 0 ? s.x() - 1 - p.sx : s.x() - p.sx / 2;
        int oz = s.dz() > 0 ? s.z() + 2 : s.dz() < 0 ? s.z() - 1 - p.sz : s.z() - p.sz / 2;
        return new Placed(p, ox, GROUND + 1 + p.yOffset, oz);
    }
}
