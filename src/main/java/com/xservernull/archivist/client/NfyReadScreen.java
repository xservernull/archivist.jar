package com.xservernull.archivist.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * A message orb's line
 */
final class NfyReadScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int GLFW_KEY_SPACE = 32;
    private static final int GLFW_KEY_ENTER = 257;
    private static final int GLFW_KEY_KP_ENTER = 335;
    private final String text;
    private List<FormattedCharSequence> wrapped = List.of();
    private int textTop;

    NfyReadScreen(String text) {
        super(Component.empty());
        this.text = text;
    }

    @Override
    protected void init() {
        int maxWidth = Math.min(width - 40, 320);
        wrapped = font.split(Lang.text(text).withStyle(ChatFormatting.ITALIC), maxWidth);
        textTop = height / 3 - ((wrapped.size() - 1) * LINE_HEIGHT) / 2;
        int buttonY = Math.min(height - BUTTON_HEIGHT - 10, textTop + wrapped.size() * LINE_HEIGHT + 16);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - BUTTON_WIDTH) / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < wrapped.size(); i++) {
            graphics.drawCenteredString(font, wrapped.get(i), width / 2, textTop + i * LINE_HEIGHT, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW_KEY_SPACE || keyCode == GLFW_KEY_ENTER || keyCode == GLFW_KEY_KP_ENTER) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public boolean isPauseScreen() { return false; }
}
