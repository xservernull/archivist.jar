// merges a translated overlay into a base qa book using plain gson
// anything the overlay leaves out stays in english
package com.xservernull.archivist.talk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

public final class QaOverlay {
    private QaOverlay() {}

    public static JsonObject merge(JsonObject base, JsonObject overlay, String lang, List<String> problems) {
        JsonObject out = base.deepCopy();

        if (overlay.has("fallbacks") && out.has("fallbacks")) {
            JsonObject fb = out.getAsJsonObject("fallbacks");
            for (Map.Entry<String, JsonElement> e : overlay.getAsJsonObject("fallbacks").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                fb.add(e.getKey(), e.getValue());
            }
        }

        if (overlay.has("synonyms")) {
            JsonObject syn = out.has("synonyms") ? out.getAsJsonObject("synonyms") : new JsonObject();
            out.add("synonyms", syn);
            for (Map.Entry<String, JsonElement> e : overlay.getAsJsonObject("synonyms").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonArray target = syn.has(e.getKey()) ? syn.getAsJsonArray(e.getKey()) : new JsonArray();
                syn.add(e.getKey(), target);
                target.addAll(e.getValue().getAsJsonArray());
            }
        }

        JsonArray entries = out.has("entries") ? out.getAsJsonArray("entries") : new JsonArray();
        out.add("entries", entries);
        if (overlay.has("entries")) {
            for (Map.Entry<String, JsonElement> e : overlay.getAsJsonObject("entries").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                JsonObject target = find(entries, e.getKey());
                if (target == null) {
                    problems.add("entry '" + e.getKey() + "' is not in the base book");
                    continue;
                }
                applyEntry(target, e.getValue().getAsJsonObject(), e.getKey(), problems);
            }
        }
        if (overlay.has("extra_entries")) entries.addAll(overlay.getAsJsonArray("extra_entries"));
        return out;
    }

    private static JsonObject find(JsonArray entries, String id) {
        for (JsonElement el : entries) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("id") && o.get("id").getAsString().equals(id)) return o;
        }
        return null;
    }

    private static void applyEntry(JsonObject target, JsonObject o, String id, List<String> problems) {
        if (o.has("match")) addTo(match(target), "any", o.getAsJsonArray("match"));
        if (o.has("none")) addTo(match(target), "none", o.getAsJsonArray("none"));
        if (!o.has("replies")) return;
        JsonArray fresh = o.getAsJsonArray("replies");
        JsonArray old = target.getAsJsonArray("replies");
        if (fresh.size() != old.size()) {
            problems.add("entry '" + id + "': " + fresh.size() + " replies but the base has " + old.size() + "; replies kept in English");
            return;
        }
        JsonArray merged = new JsonArray();
        for (int i = 0; i < old.size(); i++) {
            JsonElement was = old.get(i);
            if (was.isJsonObject()) {
                JsonObject copy = was.getAsJsonObject().deepCopy();
                copy.add("text", fresh.get(i));
                merged.add(copy);
            } else {
                merged.add(fresh.get(i));
            }
        }
        target.add("replies", merged);
    }

    private static JsonObject match(JsonObject entry) {
        if (!entry.has("match")) entry.add("match", new JsonObject());
        return entry.getAsJsonObject("match");
    }

    private static void addTo(JsonObject match, String key, JsonArray words) {
        JsonArray list = match.has(key) ? match.getAsJsonArray(key) : new JsonArray();
        match.add(key, list);
        list.addAll(words);
    }
}
