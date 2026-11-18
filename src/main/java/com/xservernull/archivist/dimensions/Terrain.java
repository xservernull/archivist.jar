package com.xservernull.archivist.dimensions;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** what one dimension generates*/
public abstract class Terrain {
    private final Map<Long, Tops> topsCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Tops> e) {
            return size() > 48;
        }
    };
    abstract void fill(Canvas c);
    public final void generate(ChunkAccess chunk) {
        ChunkCanvas c = new ChunkCanvas(chunk);
        fill(c);
        c.finish();
    }
    public final int firstFree(int x, int z, boolean oceanFloor, int minY, int maxY) {
        Tops t = tops(x >> 4, z >> 4, minY, maxY);
        int i = (z & 15) << 4 | (x & 15);
        int top = oceanFloor ? t.solid[i] : t.any[i];
        return top == Integer.MIN_VALUE ? minY : top + 1;
    }
    public final BlockState[] column(int x, int z, int minY, int maxY) {
        MemCanvas m = new MemCanvas(x & ~15, z & ~15, minY, maxY);
        fill(m);
        BlockState[] out = new BlockState[maxY - minY];
        for (int y = minY; y < maxY; y++) out[y - minY] = m.get(x & 15, y, z & 15);
        return out;
    }
    private Tops tops(int cx, int cz, int minY, int maxY) {
        long key = ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
        synchronized (topsCache) {
            Tops t = topsCache.get(key);
            if (t != null) return t;
        }
        MemCanvas m = new MemCanvas(cx << 4, cz << 4, minY, maxY);
        fill(m);
        Tops t = new Tops(m.topAny.clone(), m.topSolid.clone());
        synchronized (topsCache) {
            topsCache.put(key, t);
        }
        return t;
    }
    private static final Logger LOGGER = LogUtils.getLogger();

    private record Tops(int[] any, int[] solid) {}

    static BlockState parse(String text) {
        String id = text;
        String props = null;
        int bracket = text.indexOf('[');
        if (bracket >= 0) {
            id = text.substring(0, bracket);
            props = text.substring(bracket + 1, text.length() - 1);
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id));
        if (block.isEmpty()) {
            LOGGER.warn("[realm] unknown block '{}', using air", id);
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = block.get().defaultBlockState();
        if (props != null && !props.isEmpty()) {
            for (String kv : props.split(",")) {
                int eq = kv.indexOf('=');
                if (eq < 0) continue;
                state = withProperty(state, kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            LOGGER.warn("[realm] block {} has no property '{}'", state.getBlock(), name);
            return state;
        }
        Optional parsed = property.getValue(value);
        if (parsed.isEmpty()) {
            LOGGER.warn("[realm] block {} property '{}' has no value '{}'", state.getBlock(), name, value);
            return state;
        }
        return state.setValue(property, (Comparable) parsed.get());
    }
    static BlockState custom(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path);
        return BuiltInRegistries.BLOCK.getOptional(id)
                .orElseThrow(() -> new IllegalStateException("[realm] unknown block " + id))
                .defaultBlockState();
    }
    static int hash(int x, int y, int z, int salt) {
        long h = salt * 0x9E3779B97F4A7C15L ^ x * 0xC2B2AE3D27D4EB4FL ^ y * 0x165667B19E3779F9L ^ z * 0x27D4EB2F165667C5L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        h *= 0xD6E8FEB86659FD93L;
        h ^= h >>> 32;
        return (int) (h >>> 33);
    }
    static double firstDouble(long seed) {
        long s = (seed ^ 0x5DEECE66DL) & ((1L << 48) - 1);
        s = (s * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        int a = (int) (s >>> (48 - 26));
        s = (s * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
        int b = (int) (s >>> (48 - 27));
        return (((long) a << 27) + b) * 0x1.0p-53;
    }
    static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
