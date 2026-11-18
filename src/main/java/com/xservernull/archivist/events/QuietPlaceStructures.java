// the two buildings of archivist:quietplace (port of the quietplace part of the old
// chunkstructureplacer): the house on a grid of #house_grid chunks and the farm on a grid of

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

@EventBusSubscriber(modid = Archivist.MOD_ID)
public final class QuietPlaceStructures {
    private QuietPlaceStructures() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceKey<Level> QUIET_PLACE = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "quietplace"));

    private static final int HOUSE_GRID = 3;
    private static final int FARM_GRID = 7;
    private static final int FARM_PHASE = 1;

    private enum Kind {
        HOUSE("quietplacehouse", 1, List.of(Blocks.STRUCTURE_BLOCK, Blocks.GRASS_BLOCK, Blocks.DIRT)),
        FARM("overworldquietplacefarm", 2, List.of(Blocks.STRUCTURE_BLOCK, Blocks.GRASS_BLOCK));

        final ResourceLocation id;
        final int below;
        final List<Block> ignored;

        Kind(String path, int below, List<Block> ignored) {
            this.id = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path);
            this.below = below;
            this.ignored = ignored;
        }
    }

    private record Job(ServerLevel level, ChunkPos chunk, Kind kind) {}

    private static final Queue<Job> QUEUE = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(QUIET_PLACE) || !event.isNewChunk()) return;
        ChunkPos pos = event.getChunk().getPos();
        if (onGrid(pos.x, pos.z, HOUSE_GRID, 0)) QUEUE.add(new Job(level, pos, Kind.HOUSE));
        if (onGrid(pos.x, pos.z, FARM_GRID, FARM_PHASE) && !nearHouse(pos)) QUEUE.add(new Job(level, pos, Kind.FARM));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Job job = QUEUE.poll();
        if (job != null) place(job);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }

    private static boolean onGrid(int cx, int cz, int spacing, int phase) {
        return Math.floorMod(cx - phase, spacing) == 0 && Math.floorMod(cz - phase, spacing) == 0;
    }

    static ChunkPos nearest(boolean farm, BlockPos from) {
        int spacing = farm ? FARM_GRID : HOUSE_GRID, phase = farm ? FARM_PHASE : 0;
        int fx = from.getX() >> 4, fz = from.getZ() >> 4;
        int bx = (int) Math.round((fx - phase) / (double) spacing), bz = (int) Math.round((fz - phase) / (double) spacing);
        ChunkPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int i = -3; i <= 3; i++) {
            for (int j = -3; j <= 3; j++) {
                ChunkPos pos = new ChunkPos((bx + i) * spacing + phase, (bz + j) * spacing + phase);
                if (farm && nearHouse(pos)) continue;
                double distance = (double) (pos.x - fx) * (pos.x - fx) + (double) (pos.z - fz) * (pos.z - fz);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = pos;
                }
            }
        }
        return best;
    }

    private static boolean nearHouse(ChunkPos pos) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (onGrid(pos.x + dx, pos.z + dz, HOUSE_GRID, 0)) return true;
            }
        }
        return false;
    }

    private static long seed(ChunkPos pos, ResourceLocation id) {
        return (long) pos.x * 341873128712L + (long) pos.z * 132897987541L + (long) id.toString().hashCode() * 19L;
    }

    private static void place(Job job) {
        ServerLevel level = job.level();
        Kind kind = job.kind();
        Optional<StructureTemplate> found = level.getStructureManager().get(kind.id);
        if (found.isEmpty()) {
            LOG.warn("[quietplace] structure {} not found (data/{}/structure/{}.nbt), skipped",
                    kind.id, kind.id.getNamespace(), kind.id.getPath());
            return;
        }
        StructureTemplate template = found.get();

        Random rng = new Random(seed(job.chunk(), kind.id));
        int x = job.chunk().x * 16 + 5 + rng.nextInt(6);
        int z = job.chunk().z * 16 + 5 + rng.nextInt(6);
        Rotation rotation = Rotation.values()[rng.nextInt(Rotation.values().length)];

        // placeInWorld rotates around (0, 0, 0); shift so the rotated footprint starts at (x, z)
        Vec3i size = template.getSize();
        int sx = size.getX(), sz = size.getZ();
        int shiftX = 0, shiftZ = 0;
        switch (rotation) {
            case CLOCKWISE_90 -> shiftX = sz - 1;
            case CLOCKWISE_180 -> { shiftX = sx - 1; shiftZ = sz - 1; }
            case COUNTERCLOCKWISE_90 -> shiftZ = sx - 1;
            default -> { }
        }
        Vec3i rotated = template.getSize(rotation);
        int x1 = x + rotated.getX() - 1;
        int z1 = z + rotated.getZ() - 1;

        // generate the chunks the footprint covers, then read the ground in its centre
        for (int cx = x >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z >> 4; cz <= z1 >> 4; cz++) level.getChunk(cx, cz);
        }
        BlockPos ground = Ground.surface(level, x + rotated.getX() / 2, z + rotated.getZ() / 2);
        BlockState top = level.getBlockState(ground);
        if (top.isAir() || !top.getFluidState().isEmpty()) {
            LOG.info("[quietplace] no ground at {} for {}, skipped", ground, kind.id);
            return;
        }

        BlockPos origin = new BlockPos(x + shiftX, ground.getY() - kind.below, z + shiftZ);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .addProcessor(new BlockIgnoreProcessor(kind.ignored));
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        if (placed) {
            LOG.info("[quietplace] placed {} at {} ({})", kind.id, origin, rotation);
        } else {
            LOG.warn("[quietplace] {} was not placed at {}", kind.id, origin);
        }
    }
}