// builds the childhood home in archivist:childhoodhome (old playerspawnstructurehandler,
// rewritten).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

final class ChildhoodHomePlacer {
    private ChildhoodHomePlacer() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceLocation HOME = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "childhoodhome");

    private static final int HOME_AHEAD = 30;

    private static final int HOME_DROP = 7;

    private record Job(ServerLevel level, ResourceLocation template, int x, int z) {}

    private static final Deque<Job> QUEUE = new ArrayDeque<>();

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getTo().equals(ChildhoodHome.CHILDHOOD_HOME) && e.getEntity() instanceof ServerPlayer player) {
                check(player.serverLevel());
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(ChildhoodHome.CHILDHOOD_HOME)) {
                check(player.serverLevel());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            Job job = QUEUE.poll();
            if (job != null) run(job);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> QUEUE.clear());
    }

    private static void check(ServerLevel level) {
        if (!QUEUE.isEmpty()) return;
        Data data = Data.get(level.getServer());
        if (!data.originSet) {
            BlockPos spawn = level.getSharedSpawnPos();
            data.originSet = true;
            data.originX = spawn.getX();
            data.originZ = spawn.getZ() + HOME_AHEAD;
            data.setDirty();
        }
        if (!data.home) QUEUE.add(new Job(level, HOME, data.originX, data.originZ));
    }

    private static void run(Job job) {
        ServerLevel level = job.level();
        Optional<StructureTemplate> found = level.getStructureManager().get(job.template());
        if (found.isEmpty()) {
            LOG.warn("[childhoodhome] structure {} not found (data/{}/structure/{}.nbt), skipped",
                    job.template(), job.template().getNamespace(), job.template().getPath());
            return;
        }
        StructureTemplate template = found.get();
        // generate the chunks the structure covers, then read the real ground of its first column
        int x1 = job.x() + template.getSize().getX() - 1;
        int z1 = job.z() + template.getSize().getZ() - 1;
        for (int cx = job.x() >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = job.z() >> 4; cz <= z1 >> 4; cz++) level.getChunk(cx, cz);
        }
        BlockPos origin = new BlockPos(job.x(), Ground.surface(level, job.x(), job.z()).getY() - HOME_DROP, job.z());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        if (!placed) {
            LOG.warn("[childhoodhome] {} was not placed at {}", job.template(), origin);
            return;
        }
        Data data = Data.get(level.getServer());
        data.home = true;
        data.setDirty();
        LOG.info("[childhoodhome] placed {} at {}", job.template(), origin);
    }

    static java.util.Optional<BlockPos> spot(MinecraftServer server) {
        Data data = Data.get(server);
        return data.home ? java.util.Optional.of(new BlockPos(data.originX, 0, data.originZ)) : java.util.Optional.empty();
    }

    private static final class Data extends SavedData {
        private static final String NAME = "archivist_childhoodhome";
        boolean originSet;
        int originX, originZ;
        boolean home;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.originSet = tag.getBoolean("originSet");
            data.originX = tag.getInt("originX");
            data.originZ = tag.getInt("originZ");
            data.home = tag.getBoolean("home");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("originSet", originSet);
            tag.putInt("originX", originX);
            tag.putInt("originZ", originZ);
            tag.putBoolean("home", home);
            return tag;
        }
    }
}