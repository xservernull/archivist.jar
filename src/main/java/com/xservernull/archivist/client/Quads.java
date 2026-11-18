package com.xservernull.archivist.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** one textured square or rectangle in the x/y plane,*/
final class Quads {
    private Quads() {}
    static void draw(PoseStack poseStack, VertexConsumer buffer, float x0, float y0, float x1, float y1, int light) {
        PoseStack.Pose pose = poseStack.last();
        vertex(pose, buffer, x0, y1, 0, 0, light);
        vertex(pose, buffer, x1, y1, 1, 0, light);
        vertex(pose, buffer, x1, y0, 1, 1, light);
        vertex(pose, buffer, x0, y0, 0, 1, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, int light) {
        buffer.addVertex(pose, x, y, 0)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 0, 1);
    }
}
