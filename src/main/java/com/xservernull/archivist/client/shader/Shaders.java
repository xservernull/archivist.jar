package com.xservernull.archivist.client.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Client-only: loads shader programs when resources load
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class Shaders {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ShaderInstance look;
    private static ShaderInstance bloom;
    private static ShaderInstance ao;
    private static ShaderInstance nfysky;
    private static ShaderInstance sunsky;

    private Shaders() {}

    /** assets/archivist/shaders/core/look.*/
    public static ShaderInstance look() {
        return look;
    }
    /** assets/archivist/shaders/core/bloom*/
    public static ShaderInstance bloom() {
        return bloom;
    }
    /** assets/archivist/shaders/core/ao*/
    public static ShaderInstance ao() {
        return ao;
    }
    /** assets/archivist/shaders/core/nfysky*/
    public static ShaderInstance nfysky() {
        return nfysky;
    }
    /** assets/archivist/shaders/core/sunsky*/
    public static ShaderInstance sunsky() {
        return sunsky;
    }

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        load(event, "look", DefaultVertexFormat.POSITION_TEX, shader -> look = shader);
        load(event, "bloom", DefaultVertexFormat.POSITION_TEX, shader -> bloom = shader);
        load(event, "ao", DefaultVertexFormat.POSITION_TEX, shader -> ao = shader);
        load(event, "nfysky", DefaultVertexFormat.POSITION, shader -> nfysky = shader);
        load(event, "sunsky", DefaultVertexFormat.POSITION, shader -> sunsky = shader);
    }

    private static void load(RegisterShadersEvent event, String name, VertexFormat format, Consumer<ShaderInstance> store) {
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), "archivist:" + name, format),
                    shader -> {
                        store.accept(shader);
                        LOGGER.info("[shader] {} loaded", name);
                    });
        } catch (IOException e) {
            LOGGER.error("[shader] {} failed: {}", name, e.getMessage());
        }
    }
}