package com.xservernull.archivist.client.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;

/**
 * Client-only: registers the effects id {@code archivist:childhoodhome} (the {@code effects} field of
 * {@code dimension_type/childhoodhome.json}) and draws a fixed sunset sky with the {@code sunsky} program
 * (see {@code shaders/core/sunsky.fsh}), the same way {@link NfySky} draws its night
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class ChildhoodSky extends DimensionSpecialEffects {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "childhoodhome");

    private static final float R = 100.0F;
    private static boolean failed;

    public ChildhoodSky() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fogColor, float brightness) {
        return fogColor;
    }
    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelViewMatrix,
                             Camera camera, Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog) {
        if (failed || Shaders.sunsky() == null) return false;
        Matrix4fStack stack = RenderSystem.getModelViewStack();
        boolean pushed = false;
        try {
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(Shaders::sunsky);
            stack.pushMatrix();
            pushed = true;
            stack.mul(modelViewMatrix);
            RenderSystem.applyModelViewMatrix();

            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            quad(buffer,  R, -R, -R,  R, -R,  R,  R,  R,  R,  R,  R, -R); // +x
            quad(buffer, -R, -R,  R, -R, -R, -R, -R,  R, -R, -R,  R,  R); // -x
            quad(buffer, -R,  R, -R,  R,  R, -R,  R,  R,  R, -R,  R,  R); // +y
            quad(buffer, -R, -R,  R,  R, -R,  R,  R, -R, -R, -R, -R, -R); // -y
            quad(buffer, -R, -R,  R, -R,  R,  R,  R,  R,  R,  R, -R,  R); // +z
            quad(buffer,  R, -R, -R,  R,  R, -R, -R,  R, -R, -R, -R, -R); // -z
            BufferUploader.drawWithShader(buffer.buildOrThrow());
            return true;
        } catch (RuntimeException e) {
            failed = true;
            LOGGER.error("[shader] sunsky draw failed, using the vanilla sky: {}", e.toString());
            return false;
        } finally {
            if (pushed) {
                stack.popMatrix();
                RenderSystem.applyModelViewMatrix();
            }
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
        }
    }

    private static void quad(BufferBuilder b,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        b.addVertex(x1, y1, z1);
        b.addVertex(x2, y2, z2);
        b.addVertex(x3, y3, z3);
        b.addVertex(x4, y4, z4);
    }

    @SubscribeEvent
    public static void onRegister(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new ChildhoodSky());
    }
}
