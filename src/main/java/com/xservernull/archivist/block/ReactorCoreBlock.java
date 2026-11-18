// the old_nether reactor core, hitting it starts the fight and it can't be broken until it's done
// layer and done live in the block state so the client can swap the model
package com.xservernull.archivist.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;

public final class ReactorCoreBlock extends Block implements EntityBlock {
    public static final IntegerProperty LAYER = IntegerProperty.create("layer", 1, 3);
    public static final BooleanProperty DONE = BooleanProperty.create("done");

    public ReactorCoreBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_CYAN)
                .strength(3.0F, 6.0F)
                .sound(SoundType.STONE)
                .lightLevel(state -> state.getValue(DONE) ? 0 : 12));
        registerDefaultState(stateDefinition.any().setValue(LAYER, 1).setValue(DONE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYER, DONE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorCoreEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlocks.REACTOR_CORE_ENTITY.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<ReactorCoreEntity>) ReactorCoreEntity::tick;
    }

    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide || state.getValue(DONE) || !(player instanceof ServerPlayer hitter)) return;
        if (level.getBlockEntity(pos) instanceof ReactorCoreEntity core) core.hit(hitter);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(DONE) ? super.getDestroyProgress(state, player, level, pos) : 0.0F;
    }
}