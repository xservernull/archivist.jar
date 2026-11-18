// corruption in the overworld (port of the old corruptionsurfacespawner, plus the lava block
// of your #057 request).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

@EventBusSubscriber(modid = Archivist.MOD_ID)
public final class CorruptionSurface {
    private static final int ONE_IN = 100;
    private static final int MIN_BLOCKS = 2;
    private static final int MAX_BLOCKS = 8;
    private static final float LAVA_SHARE = 0.5f;

    private record Pending(ServerLevel level, ChunkPos pos) {}

    private static final Queue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    private CorruptionSurface() {}

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        if (!event.isNewChunk()) return;
        if (level.random.nextInt(ONE_IN) != 0) return;
        PENDING.add(new Pending(level, event.getChunk().getPos()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Pending pending;
        while ((pending = PENDING.poll()) != null) {
            place(pending.level(), pending.pos());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private static void place(ServerLevel level, ChunkPos chunk) {
        if (!level.hasChunk(chunk.x, chunk.z)) return;
        int count = MIN_BLOCKS + level.random.nextInt(MAX_BLOCKS - MIN_BLOCKS + 1);
        for (int i = 0; i < count; i++) {
            int x = chunk.getMinBlockX() + level.random.nextInt(16);
            int z = chunk.getMinBlockZ() + level.random.nextInt(16);
            BlockPos pos = Ground.surface(level, x, z);
            BlockState existing = level.getBlockState(pos);
            if (existing.isAir() || !existing.getFluidState().isEmpty()) continue;
            Supplier<Block> block = level.random.nextFloat() < LAVA_SHARE ? ModBlocks.STATIC_LAVA_BLOCK : ModBlocks.FAKE_BEDROCK;
            level.setBlock(pos, block.get().defaultBlockState(), 3);
        }
    }
}
