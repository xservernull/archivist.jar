package com.xservernull.archivist.client.shader;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Vector3f;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Client-only temporary screen effects for events
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class Fx {
    public static final int FADE = 20;

    private static final Look NEUTRAL = new Look();
    private static final Look SCRATCH = new Look();
    private static final Vector3f WHITE = new Vector3f(1f, 1f, 1f);
    private static final Vector3f WARM_RED = new Vector3f(1f, 0.35f, 0.3f);
    private static final Vector3f BLACK = new Vector3f(0f, 0f, 0f);
    private static final int FALL_ROLL = 28, FALL_DARK = 24, FALL_DARK_DELAY = 8;
    private static final float PRAYER_FADE = 0.15f;
    private static final Map<String, BiConsumer<Look, Float>> EFFECTS = Map.of(
            "greyscale", Fx::greyscale,
            "prayer", Fx::prayer,
            "infdev_fall", Fx::infdevDark,
            "infdev_arrive", Fx::infdevDark,
            "blackout", Fx::infdevDark);
    private static final java.util.Set<String> BLACK_FILL = java.util.Set.of("infdev_fall", "infdev_arrive", "blackout");

    private static String active;
    private static int age, duration;
    private static ResourceKey<Level> dimension;

    private Fx() {}

    public static boolean start(String id, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (!EFFECTS.containsKey(id) || mc.level == null || ticks <= 0) return false;
        active = id;
        age = 0;
        duration = ticks;
        dimension = mc.level.dimension();
        return true;
    }

    public static void stop() {
        active = null;
        dimension = null;
    }

    public static Look apply(Look base) {
        if (active == null) return base;
        Look from = base != null ? base : NEUTRAL;
        if (from.pixel > 0f) return base;
        SCRATCH.copyFrom(from);
        EFFECTS.get(active).accept(SCRATCH, amount());
        return SCRATCH;
    }


    public static boolean isRunning(String id) {
        return id.equals(active);
    }
    private static float amount() {
        if (active == null) return 0f;
        if ("infdev_fall".equals(active)) return Math.max(0f, Math.min(1f, (age - FALL_DARK_DELAY) / (float) FALL_DARK));
        if ("infdev_arrive".equals(active)) return Math.max(0f, 1f - age / (float) duration);
        // the way into rd132011 (events.DimensionProgress): black rises over the whole effect
        if ("blackout".equals(active)) return Math.min(1f, age / (float) duration);
        float fade = "prayer".equals(active) ? Math.max(FADE, duration * PRAYER_FADE) : FADE;
        return Math.max(0f, Math.min(1f, Math.min(age, duration - age) / fade));
    }
    private static void greyscale(Look look, float amount) {
        look.saturation = lerp(look.saturation, 0f, amount);
        look.contrast = lerp(look.contrast, look.contrast * 1.15f, amount);
        look.tint.lerp(WHITE, amount);
    }


    private static void prayer(Look look, float amount) {
        look.tint.lerp(WARM_RED, amount * 0.6f);
    }
    private static void infdevDark(Look look, float amount) {
        look.tint.lerp(BLACK, amount);
    }
    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (active == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive() || !mc.level.dimension().equals(dimension)
                || ++age >= duration) {
            stop();
        }
    }

    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post event) {
        if (active == null || !BLACK_FILL.contains(active) || dimension == null) return;
        Look base = Looks.get(dimension);
        if (base == null || base.pixel <= 0f) return;
        int alpha = Math.round(Math.max(0f, Math.min(1f, amount())) * 255f);
        if (alpha <= 0) return;
        event.getGuiGraphics().fill(0, 0, event.getGuiGraphics().guiWidth(), event.getGuiGraphics().guiHeight(), alpha << 24);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!"infdev_fall".equals(active)) return;
        float t = Math.min(1f, (age + (float) event.getPartialTick()) / FALL_ROLL);
        event.setRoll(event.getRoll() + 180f * t * t * t);
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (!"prayer".equals(active)) return;
        float amount = amount();
        event.setRed(lerp(event.getRed(), 1f, amount));
        event.setGreen(lerp(event.getGreen(), 0f, amount));
        event.setBlue(lerp(event.getBlue(), 0f, amount));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stop();
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("archivistfx")
                .then(Commands.literal("stop").executes(context -> {
                    stop();
                    return 1;
                }))
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> run(context, 200))
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 12000))
                                .executes(context -> run(context, IntegerArgumentType.getInteger(context, "ticks"))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, int ticks) {
        String id = StringArgumentType.getString(context, "id");
        boolean ok = start(id, ticks);
        context.getSource().sendSuccess(() -> Component.literal(ok
                ? "fx " + id + " for " + ticks + " ticks"
                : "unknown fx '" + id + "' (known: " + String.join(", ", EFFECTS.keySet()) + ")"), false);
        return ok ? 1 : 0;
    }
}
