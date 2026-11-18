package com.xservernull.archivist.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * the log of message orb lines the player has read,
 */
final class NfyLogScreen extends Screen {
    private static final int LINE_HEIGHT = 10;
    private static final int TOP = 32;
    private static final int BOTTOM_MARGIN = 38;
    private static final int GLFW_KEY_UP = 265;
    private static final int GLFW_KEY_DOWN = 264;
    private final List<String> entries;
    private final List<FormattedCharSequence> rows = new ArrayList<>();
    private int scroll;
    private int visibleRows;
    private int columnX;

    NfyLogScreen(List<String> entries) {
        super(Component.empty());
        this.entries = List.copyOf(entries);
    }

    @Override
    protected void init() {
        int columnWidth = Math.min(width - 60, 320);
        columnX = (width - columnWidth) / 2;
        rows.clear();
        for (String entry : entries) {
            rows.addAll(font.split(Lang.text(entry).withStyle(ChatFormatting.ITALIC), columnWidth));
            rows.add(FormattedCharSequence.EMPTY);
        }
        visibleRows = Math.max(1, (height - TOP - BOTTOM_MARGIN) / LINE_HEIGHT);
        scroll = Math.min(scroll, maxScroll());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds((width - 100) / 2, height - 28, 100, 20)
                .build());
    }
    private int maxScroll() { return Math.max(0, rows.size() - visibleRows); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Lang.text("archivist.nfy.title|hope").withStyle(ChatFormatting.YELLOW), width / 2, 14, 0xFFFFFFFF);
        if (entries.isEmpty()) {
            graphics.drawCenteredString(font, Lang.text("archivist.nfy.empty|nothing here yet").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                    width / 2, height / 3, 0xFFFFFFFF);
            return;
        }
        for (int i = 0; i < visibleRows && scroll + i < rows.size(); i++) {
            graphics.drawString(font, rows.get(scroll + i), columnX, TOP + i * LINE_HEIGHT, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(scrollY) * 3));
        return true;
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (NfyClient.LOG_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW_KEY_UP || keyCode == GLFW_KEY_DOWN) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (keyCode == GLFW_KEY_DOWN ? 1 : -1)));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override public boolean isPauseScreen() { return false; }
}
