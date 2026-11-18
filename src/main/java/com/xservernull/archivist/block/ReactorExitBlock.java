// glowing exit block that appears once the reactor is beaten, OldNetherReactor does the sending
package com.xservernull.archivist.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class ReactorExitBlock extends Block {
    public ReactorExitBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .noCollission()
                .noOcclusion()
                .lightLevel(state -> 15)
                .sound(SoundType.GLASS)
                .strength(-1.0F, 3600000.0F)
                .noLootTable());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.3F) {
            level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0.0, 0.05, 0.0);
        }
    }
}