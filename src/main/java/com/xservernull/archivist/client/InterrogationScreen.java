package com.xservernull.archivist.client;

import com.xservernull.archivist.events.ChildhoodNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * the dog's questions
 */
public final class InterrogationScreen extends Screen {
    private static final String[] QUESTIONS = {
            "archivist.interrogation.1|please dont leave me here",
            "archivist.interrogation.2|i know time has passed but i miss you",
            "archivist.interrogation.3|i dont want to be alone here no more",
            "archivist.interrogation.4|hes not you, this game makes me want to believe he is, but hes not",
            "archivist.interrogation.5|kill this entity already and i can be free",
    };
    private static final int BUTTON_WIDTH = 80, BUTTON_HEIGHT = 20, NEXT_DELAY = 3, DUST = 20;

    private final RandomSource random = RandomSource.create();
    private final int index;
    private boolean answered;
    private int answeredTicks;
    private List<FormattedCharSequence> wrapped = List.of();

    private InterrogationScreen(int index) {
        super(Component.empty());
        this.index = index;
    }

    public static void open(int index) {
        Minecraft.getInstance().setScreen(new InterrogationScreen(Math.floorMod(index, QUESTIONS.length)));
    }

    @Override
    protected void init() {
        Breathing.start();
        wrapped = font.split(Lang.text(QUESTIONS[index]), Math.min(width - 40, 400));
        int centerX = width / 2;
        int buttonY = height / 2 + 60;
        addRenderableWidget(Button.builder(Lang.text("archivist.interrogation.yes|YES"), b -> answer(false))
                .bounds(centerX - BUTTON_WIDTH - 10, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Lang.text("archivist.interrogation.no|NO"), b -> answer(false))
                .bounds(centerX + 10, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build());
        if (index == 0) {
            addRenderableWidget(Button.builder(Lang.text("archivist.interrogation.confused|I don't understand"), b -> answer(true))
                    .bounds(centerX - 60, buttonY + 30, 120, 15).build());
        }
    }

    private void answer(boolean confused) {
        if (answered) return;
        answered = true;
        if (confused && minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.7F);
        }
    }

    @Override
    public void tick() {
        if (!answered || ++answeredTicks < NEXT_DELAY) return;
        if (index + 1 < QUESTIONS.length) {
            open(index + 1);
        } else {
            PacketDistributor.sendToServer(new ChildhoodNet.Done());
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);
        if (answered) return; // a plain black frame between questions, the buttons go with the question
        int y = height / 2 - 40;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawCenteredString(font, line, width / 2, y, 0xFFFFFFFF);
            y += 12;
        }
        for (int i = 0; i < DUST; i++) {
            int x = random.nextInt(Math.max(1, width));
            int dustY = random.nextInt(Math.max(1, height));
            int size = random.nextInt(3) + 1;
            graphics.fill(x, dustY, x + size, dustY + size, 0x44FFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
}
