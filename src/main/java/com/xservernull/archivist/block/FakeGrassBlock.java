// unfinished looking grass that deletes itself when hit or clicked
// plays the glitch sound, gives a flash of darkness and counts toward Him showing up
package com.xservernull.archivist.block;

import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.events.HimEncounter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public final class FakeGrassBlock extends Block {
    public FakeGrassBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_GREEN)
                .strength(0.6F)
                .sound(SoundType.GRASS));
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        glitch(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        glitch(level, pos, player);
    }

    private static void glitch(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return;
        level.playSound(null, pos, Sounds.GLITCH.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        level.removeBlock(pos, false);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 10, 0, false, false, false));
        if (player instanceof ServerPlayer serverPlayer) HimEncounter.onGrassCleared(serverPlayer);
    }
}