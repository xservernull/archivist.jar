package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Toast;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** gives Toast her model and renderer.*/
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class ToastClient {
    private ToastClient() {}

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ToastModel.LAYER, ToastModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Toast.TYPE.get(), Renderer::new);
    }

    /**assets/archivist/textures/entity/bunny.png*/
    private static final class Renderer extends MobRenderer<Toast, ToastModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "textures/entity/bunny.png");

        Renderer(EntityRendererProvider.Context context) {
            super(context, new ToastModel(context.bakeLayer(ToastModel.LAYER)), 0.4F);
        }

        @Override
        public ResourceLocation getTextureLocation(Toast toast) { return TEXTURE; }
    }
}
