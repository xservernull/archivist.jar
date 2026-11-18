package com.xservernull.archivist;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Text keys shared by server and client (no client imports)
 */
public final class Texts {
    private Texts() {}

    public static final String PREFIX = "archivist.";
    public static boolean isKey(String s) {
        return s != null && s.startsWith(PREFIX);
    }
    public static String english(String s) {
        if (s == null) return "";
        int bar = s.indexOf('|');
        return bar > 0 && isKey(s) ? s.substring(bar + 1) : s;
    }
    public static String key(String s) {
        if (!isKey(s)) return null;
        int bar = s.indexOf('|');
        return bar > 0 ? s.substring(0, bar) : s;
    }
    public static MutableComponent component(String s) {
        String key = key(s);
        if (key == null) return Component.literal(s == null ? "" : s);
        int bar = s.indexOf('|');
        return bar > 0 ? Component.translatableWithFallback(key, s.substring(bar + 1)) : Component.translatable(key);
    }
    public static MutableComponent tr(String key, String english, Object... args) {
        return Component.translatableWithFallback(key, english, args);
    }
}
