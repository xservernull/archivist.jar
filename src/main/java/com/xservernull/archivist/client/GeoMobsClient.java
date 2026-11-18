package com.xservernull.archivist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xservernull.archivist.entity.ArchivistDragon;
import com.xservernull.archivist.entity.CopyMob;
import com.xservernull.archivist.entity.Him;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

import java.util.function.Supplier;

/**
 * Client side of the GeckoLib mobs
 */
final class GeoMobsClient {
    private GeoMobsClient() {}

    private static final float WILD_STRETCH = 1.0F;

    static void register(EntityRenderersEvent.RegisterRenderers event) {
        geo(event, Mobs.TINA);
        him(event);
        geo(event, Mobs.HOPE);
        dragon(event);
        event.registerEntityRenderer(Mobs.STATUE.get(), context -> new GeoEntityRenderer<>(context, new StatueModel()));
        geo(event, Mobs.DINNERBONE);
        copy(event, Mobs.COPY_ARROW);
        copy(event, Mobs.COPY_COBWEB);
        copy(event, Mobs.COPY_ZOMBIE);
        copy(event, Mobs.COPY_FRIENDLY);
    }

    private static <T extends Entity & GeoAnimatable> void geo(
            EntityRenderersEvent.RegisterRenderers event, Supplier<EntityType<T>> type) {
        event.registerEntityRenderer(type.get(), context -> new GeoEntityRenderer<>(context, new Model<>()));
    }

    private static void him(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Mobs.HIM.get(), context -> new GeoEntityRenderer<Him>(context, new HimModel()) {
            @Override
            public void render(Him him, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffer, int light) {
                float progress = him.getStretchProgress();
                if (progress < 0.0F && him.isWild()) progress = WILD_STRETCH; // wild: stretched all the time, like the lean in HimModel
                if (progress < 0.0F) {
                    super.render(him, yaw, partialTick, pose, buffer, light);
                    return;
                }
                pose.pushPose();
                pose.scale(1.0F, 1.0F + 7.0F * progress * progress, 1.0F);
                super.render(him, yaw, partialTick, pose, buffer, light);
                pose.popPose();
            }
        });
    }

    private static void dragon(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Mobs.ARCHIVIST_DRAGON.get(), context -> new GeoEntityRenderer<ArchivistDragon>(context, new Model<ArchivistDragon>()) {
            @Override
            public Color getRenderColor(ArchivistDragon dragon, float partialTick, int packedLight) {
                float explosion = dragon.explosionProgress();
                float alpha = dragon.fadeIn(partialTick);
                int green = 255, blue = 255;
                if (explosion >= 0.0F) {
                    alpha *= 1.0F - explosion;
                    green = blue = Math.round(255.0F * (1.0F - 0.8F * explosion));
                }
                return Color.ofARGB(Math.round(255.0F * alpha), 255, green, blue);
            }

            @Override
            public RenderType getRenderType(ArchivistDragon dragon, ResourceLocation texture, MultiBufferSource buffer, float partialTick) {
                return RenderType.entityTranslucent(texture);
            }
        });
    }

    private static void copy(EntityRenderersEvent.RegisterRenderers event, Supplier<EntityType<CopyMob>> type) {
        event.registerEntityRenderer(type.get(), context -> new GeoEntityRenderer<>(context, new SkinModel()));
    }

    private static class Model<T extends Entity & GeoAnimatable> extends GeoModel<T> {
        @Override
        public ResourceLocation getModelResource(T animatable) { return EntityAssets.geometry(animatable); }
        @Override
        public ResourceLocation getTextureResource(T animatable) { return EntityAssets.texture(animatable); }
        @Override
        public ResourceLocation getAnimationResource(T animatable) { return EntityAssets.animations(animatable); }
    }
    private static final class SkinModel extends Model<CopyMob> {
        @Override
        public ResourceLocation getTextureResource(CopyMob mob) { return Skins.texture(mob.targetPlayer()); }
    }
}