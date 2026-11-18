package com.xservernull.archivist.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mods own language (client only), chosen in the Archivist menu ArchivistConfig language
 * INDEPENDENT OF THE GAME LANGUAGE
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class Lang {
    private Lang() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final String ENGLISH = "en";

    private static Map<String, String> english = Map.of();
    private static Map<String, String> current = Map.of();
    private static String loadedCode = null;

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) Lang::load);
    }
    public static String nameOf(String code) {
        return switch (code) {
            case "en" -> "English";
            case "ru" -> "\u0420\u0443\u0441\u0441\u043a\u0438\u0439";
            case "pt" -> "Portugu\u00eas";
            case "es" -> "Espa\u00f1ol";
            case "mandanga" -> "Mandanga";
            default -> code;
        };
    }

    // ---------------------------------------------------------------- lookup

    public static String lookup(String key) {
        ensure();
        String s = current.get(key);
        return s != null ? s : english.get(key);
    }
    public static String get(String keyOrText) {
        if (keyOrText == null) return "";
        int bar = keyOrText.indexOf('|');
        if (bar > 0 && keyOrText.startsWith("archivist.")) {
            String found = lookup(keyOrText.substring(0, bar));
            return found != null ? found : keyOrText.substring(bar + 1);
        }
        if (keyOrText.startsWith("archivist.")) {
            String found = lookup(keyOrText);
            return found != null ? found : keyOrText;
        }
        return keyOrText;
    }
    public static Map<String, String> entries(String prefix) {
        ensure();
        Map<String, String> out = new java.util.TreeMap<>();
        english.forEach((key, text) -> { if (key.startsWith(prefix)) out.put(key, text); });
        current.forEach((key, text) -> { if (key.startsWith(prefix)) out.put(key, text); });
        return out;
    }
    public static String fmt(String key, Object... args) {
        String raw = get(key);
        try {
            return String.format(raw, args);
        } catch (RuntimeException e) {
            return raw;
        }
    }

    public static MutableComponent text(String keyOrText) {
        return Component.literal(get(keyOrText));
    }
    public static MutableComponent text(String key, Object... args) {
        return Component.literal(fmt(key, args));
    }
    public static String english(String keyOrText) {
        return com.xservernull.archivist.Texts.english(keyOrText);
    }

    // ---------------------------------------------------------------- loading

    /** The chosen code; English while the config is not loaded yet (early startup). */
    private static String code() {
        try {
            return ArchivistConfig.language();
        } catch (RuntimeException e) {
            return ENGLISH;
        }
    }
    private static void ensure() {
        String want = code();
        if (loadedCode == null || !loadedCode.equals(want)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) load(mc.getResourceManager());
        }
    }
    public static void load(ResourceManager manager) {
        String code = code();
        english = read(manager, ENGLISH);
        current = ENGLISH.equals(code) ? english : read(manager, code);
        loadedCode = code;
        LOG.info("[lang] {}: {} keys (english {})", code, current.size(), english.size());
    }
    public static void reload() {
        loadedCode = null;
        ensure();
    }

    private static Map<String, String> read(ResourceManager manager, String code) {
        Map<String, String> out = new HashMap<>();
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "archivist_lang/" + code + ".json");
        List<Resource> stack = manager.getResourceStack(file);
        for (Resource resource : stack) {
            try (Reader reader = resource.openAsReader()) {
                JsonObject o = GSON.fromJson(reader, JsonObject.class);
                if (o == null) continue;
                for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                    if (e.getKey().startsWith("_") || !e.getValue().isJsonPrimitive()) continue;
                    out.put(e.getKey(), e.getValue().getAsString());
                }
            } catch (Exception e) {
                LOG.error("[lang] {} could not be read: {}", file, e.toString());
            }
        }
        if (stack.isEmpty()) LOG.warn("[lang] missing {}", file);
        return out;
    }
}