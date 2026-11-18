package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.gui.screens.PauseScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.Random;

/**
 * a faint green wash over the whole screen
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class AlphaScreenFx {
    private AlphaScreenFx() {}

    private static final Random RNG = new Random();

    @SubscribeEvent
    public static void onPauseRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;
        int alpha = 4 + RNG.nextInt(8);
        event.getGuiGraphics().fill(0, 0, event.getScreen().width, event.getScreen().height, (alpha << 24) | 0x00AA00);
    }
}
