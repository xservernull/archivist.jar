// the far_lands temple: data/archivist/structure/peladoboton.nbt (62 x 69 x 86, a grass hill
// with a bare stone dome and a hollow redstone and wool machine inside) built once at a fixed

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;

public final class FarLandsTemple {
    private FarLandsTemple() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceKey<Level> FAR_LANDS =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "far_lands"));
    private static final ResourceLocation TEMPLE = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "peladoboton");

    private static final int ORIGIN_X = 69, ORIGIN_Y = 8, ORIGIN_Z = -31;
    private static final int DOME_X = 31, DOME_Z = 31, DOME_TOP = 62;
    private static final int RING_MIN = 16, RING_SPREAD = 8;

    private record Job(ServerLevel level) {}

    private static final Deque<Job> QUEUE = new ArrayDeque<>();

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getTo().equals(FAR_LANDS) && e.getEntity() instanceof ServerPlayer player) check(player.serverLevel());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(FAR_LANDS)) {
                check(player.serverLevel());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Job job = QUEUE.poll();
            if (job != null) run(job.level());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> QUEUE.clear());
    }

    // ---- for the Thems ----

    public static Vec3 centre() {
        return new Vec3(ORIGIN_X + DOME_X + 0.5, ORIGIN_Y + DOME_TOP + 1.0, ORIGIN_Z + DOME_Z + 0.5);
    }

    public static boolean built(MinecraftServer server) {
        return Data.get(server).built;
    }

    public static boolean praying(ServerLevel level) {
        return level.dimension().equals(FAR_LANDS) && GlobalPrayer.active(level.getServer());
    }

    public static Vec3 spot(ServerLevel level, UUID id) {
        long h = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        double angle = (h & 0xFFFF) / 65536.0 * (Math.PI * 2.0);
        double radius = RING_MIN + ((h >>> 16) & 0xFF) / 256.0 * RING_SPREAD;
        Vec3 centre = centre();
        double x = centre.x + Math.cos(angle) * radius;
        double z = centre.z + Math.sin(angle) * radius;
        return new Vec3(x, Ground.openY(level, (int) Math.floor(x), (int) Math.floor(z), centre.y), z);
    }

    // ---- building ----

    private static void check(ServerLevel level) {
        if (!QUEUE.isEmpty() || Data.get(level.getServer()).built) return;
        QUEUE.add(new Job(level));
    }

    private static void run(ServerLevel level) {
        Optional<StructureTemplate> found = level.getStructureManager().get(TEMPLE);
        if (found.isEmpty()) {
            LOG.warn("[farlands] structure {} not found (data/{}/structure/{}.nbt), skipped",
                    TEMPLE, TEMPLE.getNamespace(), TEMPLE.getPath());
            return;
        }
        StructureTemplate template = found.get();
        int x1 = ORIGIN_X + template.getSize().getX() - 1;
        int z1 = ORIGIN_Z + template.getSize().getZ() - 1;
        for (int cx = ORIGIN_X >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = ORIGIN_Z >> 4; cz <= z1 >> 4; cz++) level.getChunk(cx, cz);
        }
        BlockPos origin = new BlockPos(ORIGIN_X, ORIGIN_Y, ORIGIN_Z);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            LOG.warn("[farlands] {} was not placed at {}", TEMPLE, origin);
            return;
        }
        Data data = Data.get(level.getServer());
        data.built = true;
        data.setDirty();
        LOG.info("[farlands] placed {} at {}", TEMPLE, origin);
    }

    private static final class Data extends SavedData {
        private static final String NAME = "archivist_farlands_temple";
        boolean built;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.built = tag.getBoolean("built");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("built", built);
            return tag;
        }
    }
}
