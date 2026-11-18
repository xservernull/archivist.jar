package com.xservernull.archivist.dimensions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Texts;
import com.xservernull.archivist.dimensions.AetherLayout.Island;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Puts the old items back into the chests of the aether's buildings
 */
@EventBusSubscriber(modid = Archivist.MOD_ID)
public final class AetherLoot {
    private AetherLoot() {}

    private static final Logger LOG = LogUtils.getLogger();
    private record Slot(int slot, ItemStack stack) {}
    private record Spot(int x, int y, int z, List<Slot> items) {}
    private record Pending(ServerLevel level, ChunkPos pos) {}
    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();
    private static volatile Map<String, List<Spot>> spots;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Archivist.AETHER) return;
        if (event.isNewChunk()) PENDING.add(new Pending(level, event.getChunk().getPos()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Pending pending;
        while ((pending = PENDING.poll()) != null) fill(pending.level(), pending.pos());
    }
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static void fill(ServerLevel level, ChunkPos pos) {
        if (!level.hasChunk(pos.x, pos.z)) return;
        Map<String, List<Spot>> all = spots();
        place(level, pos, all.get("castle"), AetherLayout.CASTLE_X, AetherLayout.CASTLE_Y, AetherLayout.CASTLE_Z);
        Island island = AetherLayout.islandAt(pos.getMinBlockX(), pos.getMinBlockZ());
        if (island != null && island.landmarkIsland) {
            place(level, pos, all.get(island.landmark.structure), island.landmarkX(), island.plateauTop() - 1, island.landmarkZ());
        }
    }

    private static void place(ServerLevel level, ChunkPos chunk, List<Spot> list, int ox, int oy, int oz) {
        if (list == null) return;
        LevelChunk lc = level.getChunk(chunk.x, chunk.z);
        for (Spot s : list) {
            BlockPos at = new BlockPos(ox + s.x, oy + s.y, oz + s.z);
            if (at.getX() >> 4 != chunk.x || at.getZ() >> 4 != chunk.z) continue;
            BlockEntity be = lc.getBlockEntity(at, LevelChunk.EntityCreationType.IMMEDIATE);
            if (!(be instanceof Container container)) {
                LOG.warn("[aether] expected a chest or barrel at {}, found {}", at, lc.getBlockState(at));
                continue;
            }
            for (Slot slot : s.items) {
                if (slot.slot < container.getContainerSize()) container.setItem(slot.slot, slot.stack.copy());
            }
            be.setChanged();
        }
    }

    private static Map<String, List<Spot>> spots() {
        Map<String, List<Spot>> m = spots;
        if (m == null) spots = m = read();
        return m;
    }
    private static Map<String, List<Spot>> read() {
        String path = "/data/" + Archivist.MOD_ID + "/realm/aether_loot.json";
        Map<String, List<Spot>> out = new HashMap<>();
        try (InputStream in = AetherLoot.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing resource " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;   // "_comment"
                List<Spot> list = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    JsonObject o = el.getAsJsonObject();
                    JsonArray p = o.getAsJsonArray("pos");
                    List<Slot> items = new ArrayList<>();
                    for (JsonElement it : o.getAsJsonArray("items")) {
                        Slot slot = slot(it.getAsJsonObject());
                        if (slot != null) items.add(slot);
                    }
                    list.add(new Spot(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt(), items));
                }
                out.put(e.getKey(), list);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
        return out;
    }

    private static Slot slot(JsonObject o) {
        ResourceLocation id = ResourceLocation.parse(o.get("id").getAsString());
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        if (item == null) {
            LOG.warn("[aether] aether_loot.json: unknown item {}", id);
            return null;
        }
        ItemStack stack = new ItemStack(item, o.has("count") ? o.get("count").getAsInt() : 1);
        if (item == Items.WRITTEN_BOOK) {
            List<Filterable<Component>> pages = new ArrayList<>();
            for (JsonElement page : o.getAsJsonArray("pages")) pages.add(Filterable.passThrough(Texts.component(page.getAsString())));
            // unverified from memory, same constructor as events.neverforgetyou.NfyEvent.writtenBook
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                    Filterable.passThrough(o.get("title").getAsString()), o.get("author").getAsString(), 0, pages, true));
        }
        return new Slot(o.get("slot").getAsInt(), stack);
    }
}
