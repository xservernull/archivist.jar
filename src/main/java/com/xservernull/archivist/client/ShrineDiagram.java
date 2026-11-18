package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * the shrine diagram of the Quiet Place hunt, this is mostly broken and was never really checked
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class ShrineDiagram {
    private ShrineDiagram() {}
    private static final int TEX = 2000;
    private static final int PANEL = 150;
    private static final int PAD = 4, HEAD = 14, FOOT = 30, GAP = 6;
    private static final int W = PANEL + 2 * PAD, H = HEAD + PANEL + FOOT;
    private static final int GOLD = 9, TORCHES = 4;

    private static final int CARD = 0xB00B0F14;
    private static final int RIM = 0x33E6EDF3;
    private static final int ACCENT = 0xFFFFC454;
    private static final int TEXT = 0xFFE6EDF3, TEXT_DIM = 0x99E6EDF3;

    private static final ResourceLocation[] GOLD_SPRITES = new ResourceLocation[GOLD];
    private static final ResourceLocation[] TORCH_SPRITES = new ResourceLocation[TORCHES];
    private static final ResourceLocation ROCK_SPRITE = sprite("netherrack_fire");

    static {
        for (int i = 0; i < GOLD; i++) GOLD_SPRITES[i] = sprite("gold_" + (i + 1));
        for (int i = 0; i < TORCHES; i++) TORCH_SPRITES[i] = sprite("torch_" + (i + 1));
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID,
                "textures/gui/shrine_diagram/shrine_diagram_" + name + ".png");
    }

    private static int gold, netherrack, torches;
    public static void set(int goldCollected, int netherrackCollected, int torchesCollected) {
        gold = Math.min(GOLD, Math.max(0, goldCollected));
        netherrack = Math.max(0, netherrackCollected);
        torches = Math.min(TORCHES, Math.max(0, torchesCollected));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        set(0, 0, 0);
    }

    @SubscribeEvent
    public static void onRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen screen)) return;
        if (gold == 0 && netherrack == 0 && torches == 0) return;
        GuiGraphics graphics = event.getGuiGraphics();
        int x = screen.getGuiLeft() + screen.getXSize() + GAP;
        int y = screen.getGuiTop();

        // card: glass, hairline rim, amber line on top
        graphics.fill(x, y, x + W, y + H, CARD);
        graphics.fill(x, y, x + W, y + 1, RIM);
        graphics.fill(x, y + H - 1, x + W, y + H, RIM);
        graphics.fill(x, y, x + 1, y + H, RIM);
        graphics.fill(x + W - 1, y, x + W, y + H, RIM);
        graphics.fill(x + 8, y, x + W - 8, y + 1, ACCENT);
        text(graphics, Lang.get("archivist.shrine.title|SHRINE"), x + W / 2, y + 5, ACCENT, 0.75F);

        int px = x + PAD, py = y + HEAD;
        for (int i = 0; i < gold; i++) blit(graphics, GOLD_SPRITES[i], px, py);
        if (netherrack > 0) blit(graphics, ROCK_SPRITE, px, py);
        for (int i = 0; i < torches; i++) blit(graphics, TORCH_SPRITES[i], px, py);

        int ty = py + PANEL + 4;
        text(graphics, Lang.fmt("archivist.shrine.gold|%s/%s gold", gold, GOLD), x + W / 2, ty, gold == GOLD ? TEXT : TEXT_DIM, 0.75F);
        text(graphics, Lang.fmt("archivist.shrine.rock|%s/1 rock", Math.min(netherrack, 1)), x + W / 2, ty + 8, netherrack > 0 ? TEXT : TEXT_DIM, 0.75F);
        text(graphics, Lang.fmt("archivist.shrine.torch|%s/%s torch", torches, TORCHES), x + W / 2, ty + 16, torches == TORCHES ? TEXT : TEXT_DIM, 0.75F);
    }

    private static void blit(GuiGraphics g, ResourceLocation texture, int x, int y) {
        g.blit(texture, x, y, PANEL, PANEL, 0.0F, 0.0F, TEX, TEX, TEX, TEX);
    }

    private static void text(GuiGraphics g, String text, int cx, int y, int argb, float scale) {
        g.pose().pushPose();
        g.pose().translate(cx, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawCenteredString(Minecraft.getInstance().font, text, 0, 0, argb);
        g.pose().popPose();
    }
}
