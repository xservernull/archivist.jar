package com.xservernull.archivist.dimensions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xservernull.archivist.Archivist;
import net.minecraft.world.level.block.state.BlockState;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** a repeating block pattern read from data/archivist/realm/*/
final class Tile {
    final int sx, sy, sz;
    final BlockState[] cells;

    private Tile(int sx, int sy, int sz, BlockState[] cells) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.cells = cells;
    }

    static Tile load(String name) {
        String path = "/data/" + Archivist.MOD_ID + "/realm/" + name + ".json";
        try (InputStream in = Tile.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray size = o.getAsJsonArray("size");
            int sx = size.get(0).getAsInt(), sy = size.get(1).getAsInt(), sz = size.get(2).getAsInt();
            int w = o.get("cell").getAsInt();
            JsonArray pal = o.getAsJsonArray("palette");
            BlockState[] palette = new BlockState[pal.size()];
            for (int i = 0; i < palette.length; i++) {
                BlockState s = Terrain.parse(pal.get(i).getAsString());
                palette[i] = s.isAir() ? null : s;
            }
            JsonArray layers = o.getAsJsonArray("layers");
            if (layers.size() != sy) throw new IllegalStateException(path + ": expected " + sy + " layers, got " + layers.size());
            BlockState[] cells = new BlockState[sx * sy * sz];
            for (int y = 0; y < sy; y++) {
                JsonArray rows = layers.get(y).getAsJsonArray();
                if (rows.size() != sz) throw new IllegalStateException(path + ": layer " + y + " expected " + sz + " rows");
                for (int z = 0; z < sz; z++) {
                    String row = rows.get(z).getAsString();
                    if (row.length() != sx * w) throw new IllegalStateException(path + ": layer " + y + " row " + z + " has length " + row.length());
                    for (int x = 0; x < sx; x++) {
                        int idx = Integer.parseInt(row, x * w, x * w + w, 36);
                        if (idx >= palette.length) throw new IllegalStateException(path + ": palette index " + idx + " out of range");
                        cells[y * sx * sz + z * sx + x] = palette[idx];
                    }
                }
            }
            return new Tile(sx, sy, sz, cells);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
