package com.xservernull.archivist.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;

/**
 * full-screen photo
 */
public final class PhotoScreen extends Screen {
    private final ResourceLocation texture;
    private int texWidth = 200, texHeight = 200;

    private PhotoScreen(ResourceLocation texture) {
        super(Component.empty());
        this.texture = texture;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture);
             NativeImage image = NativeImage.read(in)) {
            texWidth = image.getWidth();
            texHeight = image.getHeight();
        } catch (IOException ignored) {
            // missing or unreadable file: the 200 x 200 guess stays and the blit shows the missing texture
        }
    }
    public static void open(String textureId) {
        ResourceLocation id = ResourceLocation.parse(textureId);
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "textures/" + id.getPath() + ".png");
        Minecraft.getInstance().setScreen(new PhotoScreen(file));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC000000);
        float scale = Math.min(width, height) * 0.6F / Math.max(texWidth, texHeight);
        int w = Math.max(1, Math.round(texWidth * scale));
        int h = Math.max(1, Math.round(texHeight * scale));
        graphics.blit(texture, (width - w) / 2, (height - h) / 2, w, h, 0.0F, 0.0F, texWidth, texHeight, texWidth, texHeight);
    }
    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        onClose();
        return true;
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
