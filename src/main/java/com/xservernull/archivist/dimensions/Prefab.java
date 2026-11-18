package com.xservernull.archivist.dimensions;

import com.xservernull.archivist.Archivist;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;

/**
 * A small fixed building
 */
final class Prefab {
    final String name;
    final int sx, sy, sz;
    final int yOffset;
    private final BlockState[] cells;

    private Prefab(String name, int sx, int sy, int sz, int yOffset, BlockState[] cells) {
        this.name = name;
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.yOffset = yOffset;
        this.cells = cells;
    }

    BlockState at(int x, int y, int z) {
        return cells[y * sx * sz + z * sx + x];
    }
    boolean carves(int x, int y, int z) {
        BlockState s = at(x, y, z);
        return s != null && s.isAir();
    }

    static Prefab load(String name, int yOffset) {
        return load(name, yOffset, false);
    }
    static Prefab load(String name, int yOffset, boolean keepGrass) {
        String path = "/data/" + Archivist.MOD_ID + "/structure/" + name + ".nbt";
        try (InputStream in = Prefab.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag size = root.getList("size", Tag.TAG_INT);
            int sx = size.getInt(0), sy = size.getInt(1), sz = size.getInt(2);
            ListTag paletteTag = root.getList("palette", Tag.TAG_COMPOUND);
            BlockState[] palette = new BlockState[paletteTag.size()];
            for (int i = 0; i < palette.length; i++) {
                CompoundTag p = paletteTag.getCompound(i);
                String id = p.getString("Name");
                BlockState s = Terrain.parse(stateText(id, p.getCompound("Properties")));
                // an unknown block parses to air (already logged): leave the cell alone, never dig with it
                palette[i] = s.isAir() && !id.equals("minecraft:air") ? null : s;
                // vanilla grass blocks in these files are modelling placeholders (lamp heads, ground marks), never real parts
                if (!keepGrass && id.equals("minecraft:grass_block")) palette[i] = null;
            }
            BlockState[] cells = new BlockState[sx * sy * sz];
            ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
            // when any block lies outside the box, shift by the lowest corner; the
            // blocks then span exactly the box (checked below), so the shift is not a guess.
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            boolean outside = false;
            for (int i = 0; i < blocks.size(); i++) {
                ListTag pos = blocks.getCompound(i).getList("pos", Tag.TAG_INT);
                int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                minZ = Math.min(minZ, z);
                if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) outside = true;
            }
            int shiftX = outside ? minX : 0, shiftY = outside ? minY : 0, shiftZ = outside ? minZ : 0;
            for (int i = 0; i < blocks.size(); i++) {
                CompoundTag b = blocks.getCompound(i);
                ListTag pos = b.getList("pos", Tag.TAG_INT);
                int x = pos.getInt(0) - shiftX, y = pos.getInt(1) - shiftY, z = pos.getInt(2) - shiftZ;
                if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) {
                    throw new IllegalStateException(path + ": block outside its own size at " + x + "," + y + "," + z);
                }
                cells[y * sx * sz + z * sx + x] = palette[b.getInt("state")];
            }
            return new Prefab(name, sx, sy, sz, yOffset, cells);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static String stateText(String id, CompoundTag props) {
        if (props.isEmpty()) return id;
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (String key : props.getAllKeys()) {
            if (!first) sb.append(',');
            sb.append(key).append('=').append(props.getString(key));
            first = false;
        }
        return sb.append(']').toString();
    }

    static Prefab[] oaks() {
        return new Prefab[]{oak(4), oak(5)};
    }
    static Prefab[] spruces() {
        return new Prefab[]{spruce(6), spruce(7), spruce(8)};
    }

    private static Prefab oak(int trunk) {
        int sy = trunk + 3;
        BlockState[] cells = new BlockState[5 * sy * 5];
        BlockState log = Terrain.parse("minecraft:oak_log");
        BlockState leaves = Terrain.parse("minecraft:oak_leaves[persistent=true,distance=1]");
        for (int y = 0; y < trunk; y++) cells[(y * 5 + 2) * 5 + 2] = log;
        int base = trunk - 1;
        for (int dy = 0; dy <= 2; dy++) {
            int r = dy == 0 ? 2 : 1;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dy == 0 && Math.abs(dx) == r && Math.abs(dz) == r) continue;
                    int i = ((base + dy) * 5 + 2 + dz) * 5 + 2 + dx;
                    if (cells[i] == null) cells[i] = leaves;
                }
            }
        }
        cells[((base + 3) * 5 + 2) * 5 + 2] = leaves;
        return new Prefab("oak_tree_" + trunk, 5, sy, 5, 0, cells);
    }

    private static Prefab spruce(int trunk) {
        int sy = trunk + 1;
        BlockState[] cells = new BlockState[5 * sy * 5];
        BlockState log = Terrain.parse("minecraft:spruce_log");
        BlockState leaves = Terrain.parse("minecraft:spruce_leaves[persistent=true,distance=1]");
        for (int y = 0; y < trunk; y++) cells[(y * 5 + 2) * 5 + 2] = log;
        int layers = trunk - 2;
        for (int i = 0; i < layers; i++) {
            int y = 2 + i;
            int r = Math.max(0, 2 - (i * 2 / layers));
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (r > 0 && Math.abs(dx) == r && Math.abs(dz) == r) continue;
                    if (dx == 0 && dz == 0) continue;
                    int idx = (y * 5 + 2 + dz) * 5 + 2 + dx;
                    if (cells[idx] == null) cells[idx] = leaves;
                }
            }
        }
        cells[(trunk * 5 + 2) * 5 + 2] = leaves;
        return new Prefab("spruce_tree_" + trunk, 5, sy, 5, 0, cells);
    }
}
