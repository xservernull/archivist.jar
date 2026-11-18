// solid unbreakable block that looks like lava and burns whatever touches it
package com.xservernull.archivist.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public final class StaticLavaBlock extends Block {
    private static final int FIRE_TICKS = 8 * 20;

    public StaticLavaBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.FIRE)
                .strength(-1.0F, Float.MAX_VALUE)
                .sound(SoundType.STONE)
                .lightLevel(state -> 15)
                .noOcclusion()
                .requiresCorrectToolForDrops());
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide) burn(entity, level.damageSources().lava(), 4.0F);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide) burn(entity, level.damageSources().hotFloor(), 1.0F);
        super.stepOn(level, pos, state, entity);
    }

    private static void burn(Entity entity, DamageSource source, float damage) {
        if (entity.getRemainingFireTicks() < FIRE_TICKS) entity.setRemainingFireTicks(FIRE_TICKS);
        if (entity instanceof LivingEntity living) living.hurt(source, damage);
    }
}