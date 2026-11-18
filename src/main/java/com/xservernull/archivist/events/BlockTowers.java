// xor_towers / m4kb_towers: small 2 x 2 towers, 4 to 8 blocks tall, pop up one at a time 8 to
// 20 blocks from you over about 20 seconds (8 towers).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.OptionalInt;
import java.util.function.Function;

final class BlockTowers implements QuickEvent {
    private static final int TOWERS = 8, EVERY = 50, FIRST = 10, MIN_RANGE = 8, MAX_RANGE = 20, MIN_HEIGHT = 4, MAX_HEIGHT = 8;

    private final String id;
    private final Function<RandomSource, BlockState> blocks;

    BlockTowers(String id, Function<RandomSource, BlockState> blocks) {
        this.id = id;
        this.blocks = blocks;
    }

    @Override public String id() { return id; }

    @Override
    public void start(ServerPlayer player) {
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 0.9F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        if (age >= FIRST && (age - FIRST) % EVERY == 0) raise(player);
        return age >= FIRST + EVERY * (TOWERS - 1) + 10;
    }

    private void raise(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RandomSource random = player.getRandom();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = MIN_RANGE + random.nextInt(MAX_RANGE - MIN_RANGE + 1);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!Ground.isLoaded(level, x, z) || !Ground.isLoaded(level, x + 1, z + 1)) continue;
            OptionalInt y = Ground.standY(level, x, player.getBlockY() + 6, z);
            if (y.isEmpty() || Math.abs(y.getAsInt() - player.getBlockY()) > 12) continue;
            BlockPos base = new BlockPos(x, y.getAsInt(), z);
            if (!level.getBlockState(base).isAir()) continue;
            int height = MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int h = 0; h < height; h++) {
                        BlockPos pos = base.offset(dx, h, dz);
                        if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).isAir()) break;
                        level.setBlock(pos, blocks.apply(random), Block.UPDATE_ALL);
                    }
                }
            }
            level.sendParticles(ParticleTypes.END_ROD, base.getX() + 1.0, base.getY() + height, base.getZ() + 1.0, 20, 0.5, 0.5, 0.5, 0.05);
            level.playSound(null, base, Sounds.GLITCH.get(), SoundSource.BLOCKS, 0.5F, 1.4F);
            return;
        }
    }
}
