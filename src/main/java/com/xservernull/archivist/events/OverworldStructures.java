// the overworld buildings (port of handleoverworld in the old chunkstructureplacer): a farm,
// the panopticon and jesse's tower (jessesomeonesatthedoor) in the real overworld.

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
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
public final class OverworldStructures {
    private OverworldStructures() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final int GRID = 12, ONE_IN = 4, MAX_SLOPE = 8;

    private enum Kind {
        FARM("overworldquietplacefarm", 0, 2, List.of(Blocks.STRUCTURE_BLOCK, Blocks.GRASS_BLOCK, Blocks.AIR)),
        PANOPTICON("overworldpanopticon", 3, 0, List.of(Blocks.STRUCTURE_BLOCK, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.AIR)),
        DOOR("jessesomeonesatthedoor", 6, 0, List.of(Blocks.STRUCTURE_BLOCK, Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.AIR));

        final ResourceLocation id;
        final int phase;
        final int below;
        final List<Block> ignored;

        Kind(String path, int phase, int below, List<Block> ignored) {
            this.id = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path);
            this.phase = phase;
            this.below = below;
            this.ignored = ignored;
        }
    }

    private record Job(ServerLevel level, ChunkPos chunk, Kind kind) {}

    private static final Queue<Job> QUEUE = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD) || !event.isNewChunk()) return;
        ChunkPos pos = event.getChunk().getPos();
        for (Kind kind : Kind.values()) {
            if (onGrid(pos.x, pos.z, kind.phase) && new Random(seed(pos, kind.id)).nextInt(ONE_IN) == 0) {
                QUEUE.add(new Job(level, pos, kind));
            }
        }
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

    private static boolean onGrid(int cx, int cz, int phase) {
        return Math.floorMod(cx - phase, GRID) == 0 && Math.floorMod(cz - phase, GRID) == 0;
    }

    private static long seed(ChunkPos pos, ResourceLocation id) {
        return (long) pos.x * 341873128712L + (long) pos.z * 132897987541L + (long) id.toString().hashCode() * 19L;
    }

    private static BlockPos ground(ServerLevel level, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
        while (pos.getY() > level.getMinBuildHeight() + 1) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.isAir()
                    || (state.canBeReplaced() && state.getFluidState().isEmpty())) {
                pos.move(0, -1, 0);
            } else {
                break;
            }
        }
        BlockState top = level.getBlockState(pos);
        if (top.isAir() || !top.getFluidState().isEmpty()) return null;
        return pos.immutable();
    }

    private static void place(Job job) {
        ServerLevel level = job.level();
        Kind kind = job.kind();
        Optional<StructureTemplate> found = level.getStructureManager().get(kind.id);
        if (found.isEmpty()) {
            LOG.warn("[overworld] structure {} not found (data/{}/structure/{}.nbt), skipped",
                    kind.id, kind.id.getNamespace(), kind.id.getPath());
            return;
        }
        StructureTemplate template = found.get();

        Random rng = new Random(seed(job.chunk(), kind.id));
        rng.nextInt(ONE_IN); // the draw that chose this chunk
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

        // generate the chunks the footprint covers, then read the ground in its centre and corners
        for (int cx = x >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z >> 4; cz <= z1 >> 4; cz++) level.getChunk(cx, cz);
        }
        BlockPos ground = ground(level, x + rotated.getX() / 2, z + rotated.getZ() / 2);
        if (ground == null) {
            LOG.info("[overworld] no ground (water or empty) at the centre of {} near {},{}, skipped", kind.id, x, z);
            return;
        }
        int[][] corners = {{x, z}, {x1, z}, {x, z1}, {x1, z1}};
        for (int[] corner : corners) {
            BlockPos at = ground(level, corner[0], corner[1]);
            if (at == null || Math.abs(at.getY() - ground.getY()) > MAX_SLOPE) {
                LOG.info("[overworld] {} near {},{} skipped: the ground under it is too steep or wet", kind.id, x, z);
                return;
            }
        }

        BlockPos origin = new BlockPos(x + shiftX, ground.getY() - kind.below, z + shiftZ);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(Mirror.NONE)
                .addProcessor(new BlockIgnoreProcessor(kind.ignored));
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        if (placed) {
            LOG.info("[overworld] placed {} at {} ({})", kind.id, origin, rotation);
        } else {
            LOG.warn("[overworld] {} was not placed at {}", kind.id, origin);
        }
    }
}