// the blocks the path and tower events build with.

package com.xservernull.archivist.events;

import com.xservernull.archivist.block.ModBlocks;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

final class CubeBlocks {
    private CubeBlocks() {}

    private static final List<Supplier<Block>> M4KB = List.of(
            ModBlocks.GRASS_4KB, ModBlocks.DIRT_4KB, ModBlocks.STONE_4KB,
            ModBlocks.BRICKS_4KB, ModBlocks.LOG_4KB, ModBlocks.LEAVES_4KB);

    static BlockState xor(RandomSource random) {
        return ModBlocks.XOR_BLOCK.get().defaultBlockState();
    }

    static BlockState m4kb(RandomSource random) {
        BlockState state = M4KB.get(random.nextInt(M4KB.size())).get().defaultBlockState();
        // placed leaves must not decay away under the player
        return state.hasProperty(LeavesBlock.PERSISTENT) ? state.setValue(LeavesBlock.PERSISTENT, true) : state;
    }
}
