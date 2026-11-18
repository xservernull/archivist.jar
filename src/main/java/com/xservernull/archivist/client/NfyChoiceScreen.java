package com.xservernull.archivist.client;

import com.xservernull.archivist.events.neverforgetyou.NfyNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.function.IntFunction;

/**
 * hope waits for the player
 */
final class NfyChoiceScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 4;

    private final List<String> labels;
    private final IntFunction<CustomPacketPayload> onPick;
    private boolean sent;

    NfyChoiceScreen(List<String> labels, IntFunction<CustomPacketPayload> onPick) {
        super(Component.empty());
        this.labels = List.copyOf(labels);
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        int count = labels.size();
        int top = height / 2 - (count * (BUTTON_HEIGHT + GAP)) / 2 + 20;
        for (int i = 0; i < count; i++) {
            int index = i;
            Component text = Lang.text(labels.get(i));
            int buttonWidth = Math.min(this.width - 40, Math.max(120, font.width(text) + 24));
            addRenderableWidget(Button.builder(text, button -> pick(index))
                    .bounds((this.width - buttonWidth) / 2, top + i * (BUTTON_HEIGHT + GAP), buttonWidth, BUTTON_HEIGHT)
                    .build());
        }
    }

    private void pick(int index) {
        if (sent) return;
        sent = true;
        PacketDistributor.sendToServer(onPick.apply(index));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int number = keyCode - 49; // GLFW_KEY_1
        if (number >= 0 && number < labels.size()) {
            pick(number);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public boolean isPauseScreen() { return false; }

    @Override public boolean shouldCloseOnEsc() { return false; }
}
