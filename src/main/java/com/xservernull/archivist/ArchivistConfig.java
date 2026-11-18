package com.xservernull.archivist;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Menu options.
 * <ul>
 *   <li>{@code shaders} (client file {@code archivist-client.toml}): our own post-process looks on or off.
 *       No restart needed, {@code PostFx} just reads it each frame.</li>
 *   <li>{@code animal_cannibalism} (common file {@code archivist-common.toml}): animals in the overworld
 *       attack each other near a player. Read by the server, so it matters in singleplayer or on your own server.</li>
 * </ul>
 * Call {@link #register(ModContainer)} once from the {@code @Mod} constructor.
 */

public final class ArchivistConfig {
    private ArchivistConfig() {}

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue SHADERS = CLIENT_BUILDER
            .comment("Archivist's own screen shaders. false = vanilla look in every dimension.")
            .define("shaders", true);
    private static final ModConfigSpec.IntValue LOUD = CLIENT_BUILDER
            .comment("Loud scare sounds (die, heartbeat, necksnap, alarm, glitch, scream1) in percent. 0 = silent.")
            .defineInRange("loud_noises", 100, 0, 100);
    public static final java.util.List<String> LANGUAGES = java.util.Arrays.asList("en", "ru", "pt", "es", "mandanga");
    private static final ModConfigSpec.ConfigValue<String> LANGUAGE = CLIENT_BUILDER
            .comment("Language of the mod's own text (menus, messages, Hope). One of: en, ru, pt, es, mandanga.")
            .defineInList("language", "en", LANGUAGES);
    private static final ModConfigSpec.BooleanValue CUSTOM_MENU = CLIENT_BUILDER
            .comment("Archivist's own title and pause menus. false = vanilla menus, plus a small Archivist button for the mod's config screen.")
            .define("custom_menu", true);
    public static final ModConfigSpec CLIENT = CLIENT_BUILDER.build();
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue CANNIBALISM = COMMON_BUILDER
            .comment("Overworld animals attack each other when a player is near. false = they do not.")
            .define("animal_cannibalism", true);
    private static final ModConfigSpec.IntValue DIFFICULTY = COMMON_BUILDER
            .comment("0 Harmless, 1 Normal, 2 Violent, 3 Custom. Custom uses the four numbers below as they are.")
            .defineInRange("difficulty", 1, 0, 3);
    private static final ModConfigSpec.IntValue THEM_COOLDOWN = COMMON_BUILDER
            .comment("Ticks between Them spawns in far_lands (20 ticks = 1 s).")
            .defineInRange("them_cooldown_ticks", 300, 20, 2000);
    private static final ModConfigSpec.IntValue MAX_THEM = COMMON_BUILDER
            .comment("Most Them near one player in far_lands.")
            .defineInRange("max_them", 8, 1, 64);
    private static final ModConfigSpec.IntValue MANSION_TINAS = COMMON_BUILDER
            .comment("Tinas that hunt a player in the mansion.")
            .defineInRange("mansion_tinas", 2, 0, 30);
    private static final ModConfigSpec.IntValue ECHO_TINAS = COMMON_BUILDER
            .comment("Tinas that hunt a player in the echo chamber.")
            .defineInRange("echo_tinas", 1, 0, 10);
    public static final ModConfigSpec COMMON = COMMON_BUILDER.build();

    public static final String[] DIFFICULTY_NAMES = {"Harmless", "Normal", "Violent", "Custom"};
    private static final int[][] PRESETS = {
            {1000, 2, 0, 0},
            {300, 8, 2, 1},
            {100, 20, 10, 3},
    };

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CLIENT);
        container.registerConfig(ModConfig.Type.COMMON, COMMON);
    }

    public static boolean shaders() {
        return SHADERS.get();
    }

    public static void setShaders(boolean on) {
        SHADERS.set(on);
        CLIENT.save();
    }

    public static boolean customMenu() { return CUSTOM_MENU.get(); }

    public static void setCustomMenu(boolean on) {
        CUSTOM_MENU.set(on);
        CLIENT.save();
    }

    public static String language() { return LANGUAGE.get(); }

    public static void setLanguage(String code) {
        LANGUAGE.set(code);
        CLIENT.save();
    }

    public static int loud() { return LOUD.get(); }
    public static void setLoud(int percent) { LOUD.set(percent); }

    public static int difficulty() { return DIFFICULTY.get(); }
    public static int themCooldown() { return THEM_COOLDOWN.get(); }
    public static int maxThem() { return MAX_THEM.get(); }
    public static int mansionTinas() { return MANSION_TINAS.get(); }
    public static int echoTinas() { return ECHO_TINAS.get(); }
    public static void setThemCooldown(int v) { THEM_COOLDOWN.set(v); }
    public static void setMaxThem(int v) { MAX_THEM.set(v); }
    public static void setMansionTinas(int v) { MANSION_TINAS.set(v); }
    public static void setEchoTinas(int v) { ECHO_TINAS.set(v); }
    public static void setDifficulty(int idx) {
        DIFFICULTY.set(idx);
        if (idx >= 0 && idx < PRESETS.length) {
            int[] v = PRESETS[idx];
            THEM_COOLDOWN.set(v[0]);
            MAX_THEM.set(v[1]);
            MANSION_TINAS.set(v[2]);
            ECHO_TINAS.set(v[3]);
        }
    }


    public static void save() {
        CLIENT.save();
        COMMON.save();
    }

    public static boolean cannibalism() {
        return CANNIBALISM.get();
    }

    public static void setCannibalism(boolean on) {
        CANNIBALISM.set(on);
        COMMON.save();
    }
}