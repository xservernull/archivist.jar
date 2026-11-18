package com.xservernull.archivist.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Client-only: once per frame, after the level is drawn (before the HUD and the held item)
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class PostFx {
    private static final int BLOOM_SHRINK = 4;
    private static final float BLOOM_SOFT = 0.25f;
    private static final float BLOOM_SPREAD = 1.5f;

    private static TextureTarget target;
    private static TextureTarget bloomA, bloomB;
    private static TextureTarget aoTarget;

    private PostFx() {}

    @SubscribeEvent
    public static void onStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Look look = Fx.apply(Looks.get(mc.level.dimension()));
        if (look == null) return;

        RenderTarget main = mc.getMainRenderTarget();
        if (look.pixel > 0f) {
            pixelate(main, look.pixel);
            return;
        }

        ShaderInstance shader = Shaders.look();
        if (shader == null) return;
        target = fit(target, main.width, main.height, false);

        // bloom first (it reads the untouched picture); when off, the sampler just gets the main picture
        int glow = main.getColorTextureId();
        ShaderInstance bloom = Shaders.bloom();
        float strength = 0f;
        if (look.bloomStrength > 0f && bloom != null) {
            int bw = Math.max(1, main.width / BLOOM_SHRINK);
            int bh = Math.max(1, main.height / BLOOM_SHRINK);
            bloomA = fit(bloomA, bw, bh, true);
            bloomB = fit(bloomB, bw, bh, true);
            bloomPasses(bloom, main, look);
            glow = bloomA.getColorTextureId();
            strength = look.bloomStrength;
        }

        // half-resolution AO (reads depth only); when off, the sampler gets the main picture and look.fsh never reads it
        int occlusion = main.getColorTextureId();
        float aoStrength = 0f;
        ShaderInstance ao = Shaders.ao();
        if (look.aoStrength > 0f && ao != null) {
            aoTarget = fit(aoTarget, Math.max(1, main.width / 2), Math.max(1, main.height / 2), false);
            aoPass(ao, main, look, event.getProjectionMatrix());
            occlusion = aoTarget.getColorTextureId();
            aoStrength = look.aoStrength;
        }

        // draw the look over the whole picture into our own target (it cannot read and write the main one)
        RenderSystem.disableDepthTest();
        shader.setSampler("DiffuseSampler", main.getColorTextureId());
        shader.setSampler("DepthSampler", main.getDepthTextureId());
        shader.setSampler("BloomSampler", glow);
        shader.setSampler("AoSampler", occlusion);
        setUniforms(shader, look, strength, aoStrength, event.getProjectionMatrix());
        draw(shader, target);

        // copy the result back into the main picture
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, main.width, main.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
        RenderSystem.enableDepthTest();
    }

    private static void pixelate(RenderTarget main, float scale) {
        int w = Math.max(1, (int) (main.width * scale));
        int h = Math.max(1, (int) (main.height * scale));
        target = fit(target, w, h, false);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, main.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, main.width, main.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(true);
    }

    private static void aoPass(ShaderInstance ao, RenderTarget main, Look look, Matrix4f projection) {
        RenderSystem.disableDepthTest();
        ao.setSampler("DepthSampler", main.getDepthTextureId());
        ao.safeGetUniform("InverseProjMat").set(new Matrix4f(projection).invert());
        ao.safeGetUniform("Ao").set(look.aoStrength, look.aoRadius, look.aoFade);
        draw(ao, aoTarget);
    }

    private static void bloomPasses(ShaderInstance bloom, RenderTarget main, Look look) {
        RenderSystem.disableDepthTest();
        bloom.setSampler("DepthSampler", main.getDepthTextureId());

        // 1) bright parts of the picture, shrunk: main -> bloomA
        bloom.setSampler("DiffuseSampler", main.getColorTextureId());
        bloom.safeGetUniform("BlurStep").set(0f, 0f);
        bloom.safeGetUniform("Threshold").set(look.bloomThreshold, BLOOM_SOFT);
        draw(bloom, bloomA);

        // 2) blur across: bloomA -> bloomB
        bloom.setSampler("DiffuseSampler", bloomA.getColorTextureId());
        bloom.safeGetUniform("BlurStep").set(BLOOM_SPREAD / bloomA.width, 0f);
        draw(bloom, bloomB);

        // 3) blur down: bloomB -> bloomA
        bloom.setSampler("DiffuseSampler", bloomB.getColorTextureId());
        bloom.safeGetUniform("BlurStep").set(0f, BLOOM_SPREAD / bloomA.height);
        draw(bloom, bloomA);
    }

    private static void draw(ShaderInstance shader, RenderTarget into) {
        into.bindWrite(true);
        RenderSystem.setShader(() -> shader);
        shader.apply();
        BufferBuilder quad = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        quad.addVertex(-1f, -1f, 0f).setUv(0f, 0f);
        quad.addVertex(1f, -1f, 0f).setUv(1f, 0f);
        quad.addVertex(1f, 1f, 0f).setUv(1f, 1f);
        quad.addVertex(-1f, 1f, 0f).setUv(0f, 1f);
        BufferUploader.drawWithShader(quad.buildOrThrow());
        shader.clear();
    }

    private static TextureTarget fit(TextureTarget t, int w, int h, boolean smooth) {
        if (t == null) {
            t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        } else if (t.width != w || t.height != h) {
            t.resize(w, h, Minecraft.ON_OSX);
        } else {
            return t;
        }
        if (smooth) t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    private static void setUniforms(ShaderInstance s, Look l, float bloom, float aoStrength, Matrix4f projection) {
        s.safeGetUniform("InverseProjMat").set(new Matrix4f(projection).invert());
        s.safeGetUniform("Darkness").set(l.darkness);
        s.safeGetUniform("Beam").set(l.beamFull, l.beamEnd);
        s.safeGetUniform("Reach").set(l.reachFull, l.reachEnd);
        s.safeGetUniform("Tint").set(l.tint.x, l.tint.y, l.tint.z);
        s.safeGetUniform("Saturation").set(l.saturation);
        s.safeGetUniform("Contrast").set(l.contrast);
        s.safeGetUniform("HazeColor").set(l.fogColor.x, l.fogColor.y, l.fogColor.z);
        s.safeGetUniform("Fog").set(l.fogStart, l.fogEnd);
        s.safeGetUniform("Vignette").set(l.vignette);
        s.safeGetUniform("Grain").set(l.grain);
        s.safeGetUniform("Ao").set(aoStrength, l.aoRadius, l.aoFade);
        s.safeGetUniform("Bloom").set(bloom);
        s.safeGetUniform("Flicker").set(l.flicker);
        s.safeGetUniform("Breathe").set(l.breathe);
        s.safeGetUniform("Warp").set(l.warp);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        if (bloomA != null) {
            bloomA.destroyBuffers();
            bloomA = null;
        }
        if (bloomB != null) {
            bloomB.destroyBuffers();
            bloomB = null;
        }
        if (aoTarget != null) {
            aoTarget.destroyBuffers();
            aoTarget = null;
        }
    }
}
