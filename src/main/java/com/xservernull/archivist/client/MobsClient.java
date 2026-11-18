package com.xservernull.archivist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.block.ModBlocks;
import com.xservernull.archivist.entity.FakeDoor;
import com.xservernull.archivist.entity.GlowOrb;
import com.xservernull.archivist.entity.MansionDrawing;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Tenature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * renderers for the mobs registered in Mobs i fucking hate this file
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class MobsClient {
    private MobsClient() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Mobs.FRIEND.get(), Humanoid::new);
        event.registerEntityRenderer(Mobs.PIGMEN.get(), Humanoid::new);
        event.registerEntityRenderer(Mobs.SOMEHOW.get(), Humanoid::new);
        event.registerEntityRenderer(Mobs.THEM.get(), Humanoid::new);
        event.registerEntityRenderer(Mobs.HEROBRINE.get(), Humanoid::new);
        event.registerEntityRenderer(Mobs.TENATURE.get(), TenatureRenderer::new);
        event.registerEntityRenderer(Mobs.DOG.get(), DogRenderer::new);
        event.registerEntityRenderer(Mobs.FAKE_DOOR.get(), DoorRenderer::new);
        event.registerEntityRenderer(Mobs.GLOW_ORB.get(), OrbRenderer::new);
        event.registerEntityRenderer(Mobs.MANSION_DRAWING.get(), DrawingRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.REACTOR_CORE_ENTITY.get(), ReactorCoreRenderer::new);
        GeoMobsClient.register(event);
    }
    private static ResourceLocation item(String name) {
        return ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "textures/item/" + name + ".png");
    }
    private static final class Humanoid<T extends Mob> extends HumanoidMobRenderer<T, HumanoidModel<T>> {
        Humanoid(EntityRendererProvider.Context context) {
            super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) { return EntityAssets.texture(entity); }

        @Override
        protected boolean shouldShowName(T entity) { return false; }
    }

    private static final class TenatureRenderer extends LivingEntityRenderer<Tenature, PlayerModel<Tenature>> {
        private final PlayerModel<Tenature> wide;
        private final PlayerModel<Tenature> slim;

        TenatureRenderer(EntityRendererProvider.Context context) {
            super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
            this.wide = model;
            this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        }

        @Override
        public void render(Tenature entity, float yaw, float partialTick, PoseStack poseStack,
                MultiBufferSource buffer, int light) {
            AbstractClientPlayer skin = Skins.player(entity.skinTarget().orElse(null));
            model = skin != null && skin.getSkin().model() == PlayerSkin.Model.SLIM ? slim : wide;
            super.render(entity, yaw, partialTick, poseStack, buffer, light);
        }
        @Override
        public ResourceLocation getTextureLocation(Tenature entity) {
            return Skins.texture(entity.skinTarget().orElse(null));
        }
        @Override
        protected boolean shouldShowName(Tenature entity) { return false; }
    }
    private static final class DogRenderer extends WolfRenderer {
        DogRenderer(EntityRendererProvider.Context context) { super(context); }

        @Override
        public ResourceLocation getTextureLocation(Wolf wolf) { return EntityAssets.texture(wolf); }
    }
    private static final class DoorRenderer extends EntityRenderer<FakeDoor> {
        DoorRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.0F;
        }

        @Override
        public void render(FakeDoor entity, float yaw, float partialTick, PoseStack poseStack,
                MultiBufferSource buffer, int light) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-yaw + 180.0F));
            Quads.draw(poseStack, buffer.getBuffer(RenderType.entityCutout(getTextureLocation(entity))),
                    -0.5F, 0.0F, 0.5F, 2.0F, light);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffer, light);
        }

        @Override
        public ResourceLocation getTextureLocation(FakeDoor entity) { return EntityAssets.texture(entity); }
    }

    private static final class OrbRenderer extends EntityRenderer<GlowOrb> {
        OrbRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.0F;
        }

        @Override
        public void render(GlowOrb entity, float yaw, float partialTick, PoseStack poseStack,
                MultiBufferSource buffer, int light) {
            poseStack.pushPose();
            poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());
            Quads.draw(poseStack, buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                    -0.5F, -0.5F, 0.5F, 0.5F, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffer, light);
        }

        @Override
        public ResourceLocation getTextureLocation(GlowOrb entity) { return EntityAssets.texture(entity); }
    }

    private static final class DrawingRenderer extends EntityRenderer<MansionDrawing> {
        private static final ResourceLocation[] TEXTURES = {
            item("drawing_dog"), item("drawing_sun"), item("drawing_house")
        };

        DrawingRenderer(EntityRendererProvider.Context context) {
            super(context);
            shadowRadius = 0.0F;
        }

        @Override
        public void render(MansionDrawing entity, float yaw, float partialTick, PoseStack poseStack,
                MultiBufferSource buffer, int light) {
            poseStack.pushPose();
            switch (entity.facing()) {
                case MansionDrawing.FACE_FLOOR -> poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                case MansionDrawing.FACE_CEILING -> poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
                case MansionDrawing.FACE_SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
                case MansionDrawing.FACE_EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
                case MansionDrawing.FACE_WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
                default -> { }
            }
            Quads.draw(poseStack, buffer.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                    -0.45F, -0.45F, 0.45F, 0.45F, LightTexture.FULL_BRIGHT);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffer, light);
        }

        @Override
        public ResourceLocation getTextureLocation(MansionDrawing entity) {
            return TEXTURES[Math.floorMod(entity.kind(), TEXTURES.length)];
        }
    }
}
