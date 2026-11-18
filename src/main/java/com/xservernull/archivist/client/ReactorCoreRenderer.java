package com.xservernull.archivist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xservernull.archivist.block.ReactorCoreBlock;
import com.xservernull.archivist.block.ReactorCoreEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * draws the reactor core spinning and bobbing
 */
public final class ReactorCoreRenderer implements BlockEntityRenderer<ReactorCoreEntity> {
    private static final float[] SPEED = {1.0F, 1.6F, 2.4F};
    private static final double BOB = 0.1, BOB_SPEED = 0.05;

    private final ModelBlockRenderer renderer;

    public ReactorCoreRenderer(BlockEntityRendererProvider.Context context) {
        renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
    }

    @Override
    public void render(ReactorCoreEntity core, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = core.getBlockState();
        boolean done = state.getValue(ReactorCoreBlock.DONE);
        float age = (core.getLevel() == null ? 0L : core.getLevel().getGameTime()) + partialTick;
        float angle = done ? 0.0F : (age * SPEED[state.getValue(ReactorCoreBlock.LAYER) - 1]) % 360.0F;
        double bob = done ? 0.0 : Math.sin(age * BOB_SPEED) * BOB;
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        pose.pushPose();
        pose.translate(0.5, 0.5 + bob, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-0.5, -0.5, -0.5);
        renderer.renderModel(pose.last(), buffers.getBuffer(ItemBlockRenderTypes.getChunkRenderType(state)), state, model,
                1.0F, 1.0F, 1.0F, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(ReactorCoreEntity core) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
