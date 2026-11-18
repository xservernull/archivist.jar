// glowing slippery cloud block for the aether, slows you a bit and puffs cloud particles
package com.xservernull.archivist.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;

public final class CloudBlock extends Block {
    public CloudBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                .strength(0.5F, 0.5F)
                .sound(SoundType.WOOL)
                .friction(0.95F)
                .speedFactor(0.9F)
                .noOcclusion()
                .lightLevel(state -> 15));
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity.onGround() && entity.getY() > pos.getY() + 0.1F) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x * 0.98, motion.y, motion.z * 0.98);
        }
        super.stepOn(level, pos, state, entity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextFloat() < 0.05F) {
            level.addParticle(ParticleTypes.CLOUD,
                    pos.getX() + random.nextDouble(), pos.getY() + 0.1, pos.getZ() + random.nextDouble(),
                    (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.5);
        }
        if (random.nextFloat() < 0.08F) {
            double angle = random.nextDouble() * Math.PI * 2;
            level.addParticle(ParticleTypes.GLOW,
                    pos.getX() + 0.5 + Math.cos(angle) * 0.7,
                    pos.getY() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getZ() + 0.5 + Math.sin(angle) * 0.7,
                    Math.cos(angle) * 0.1, 0, Math.sin(angle) * 0.1);
        }
    }
}
