// xor_path: for 30 seconds the world lays a 3 x 3 floor of blocks one block under your feet
// wherever there is air, so you can walk over the gaps between cubes (and jump up in a stair

package com.xservernull.archivist.events;

import com.xservernull.archivist.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Function;

final class BlockTrail implements QuickEvent {
    private static final int DURATION = 20 * 30;

    private final String id;
    private final Function<RandomSource, BlockState> blocks;

    BlockTrail(String id, Function<RandomSource, BlockState> blocks) {
        this.id = id;
        this.blocks = blocks;
    }

    @Override public String id() { return id; }

    @Override
    public void start(ServerPlayer player) {
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 1.0F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        lay(player, blocks);
        return age >= DURATION;
    }

    static void lay(ServerPlayer player, Function<RandomSource, BlockState> blocks) {
        if (player.isSpectator()) return;
        ServerLevel level = player.serverLevel();
        int y = Mth.floor(player.getY()) - 1;
        int cx = Mth.floor(player.getX()), cz = Mth.floor(player.getZ());
        boolean placed = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = new BlockPos(cx + dx, y, cz + dz);
                if (!level.isLoaded(pos) || level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).isAir()) continue;
                level.setBlock(pos, blocks.apply(player.getRandom()), Block.UPDATE_ALL);
                placed = true;
            }
        }
        if (placed) level.playSound(null, player.blockPosition(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.4F, 0.8F + player.getRandom().nextFloat() * 0.4F);
    }
}
