// which quick events may start where, read from data/archivist/events/schedule.json.

package com.xservernull.archivist.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record Schedule(int minTicks, int maxTicks, Map<String, List<Entry>> dimensions) {
    public static final Schedule EMPTY = new Schedule(30 * 20, 60 * 20, Map.of());

    public record Entry(String event, int weight, int cooldownTicks, double chance) {}

    public record Result(Schedule schedule, List<String> issues) {}

    public List<Entry> forDimension(String dimensionId) {
        return dimensions.getOrDefault(dimensionId, List.of());
    }

    public boolean isEmpty() { return dimensions.isEmpty(); }

    public static Result parse(String source, Reader reader) {
        List<String> issues = new ArrayList<>();
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return fail(source, issues, "the file is not a JSON object");
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            return fail(source, issues, "not valid JSON: " + e.getMessage());
        }

        int min = EMPTY.minTicks(), max = EMPTY.maxTicks();
        JsonElement interval = root.get("interval_seconds");
        if (interval != null) {
            if (interval.isJsonArray() && interval.getAsJsonArray().size() == 2 && isNumbers(interval.getAsJsonArray())) {
                int a = interval.getAsJsonArray().get(0).getAsInt();
                int b = interval.getAsJsonArray().get(1).getAsInt();
                if (a >= 1 && b >= a) {
                    min = a * 20;
                    max = b * 20;
                } else {
                    issues.add(source + " interval_seconds: needs 1 <= min <= max, using 30 to 60");
                }
            } else {
                issues.add(source + " interval_seconds: needs [min, max] as numbers, using 30 to 60");
            }
        }

        Map<String, List<Entry>> dimensions = new LinkedHashMap<>();
        JsonElement dims = root.get("dimensions");
        if (dims == null || !dims.isJsonObject()) {
            issues.add(source + " dimensions: missing or not an object, nothing is scheduled");
        } else {
            for (Map.Entry<String, JsonElement> d : dims.getAsJsonObject().entrySet()) {
                if (d.getKey().startsWith("_")) continue;
                if (!d.getValue().isJsonArray()) {
                    issues.add(source + " dimensions." + d.getKey() + ": not a list, dropped");
                    continue;
                }
                List<Entry> entries = new ArrayList<>();
                JsonArray array = d.getValue().getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    Entry entry = entry(source + " dimensions." + d.getKey() + "[" + i + "]", array.get(i), issues);
                    if (entry != null) entries.add(entry);
                }
                if (!entries.isEmpty()) dimensions.put(d.getKey(), List.copyOf(entries));
            }
        }
        return new Result(new Schedule(min, max, Map.copyOf(dimensions)), issues);
    }

    private static Entry entry(String where, JsonElement element, List<String> issues) {
        if (!element.isJsonObject()) {
            issues.add(where + ": not an object, dropped");
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        if (!o.has("event") || !o.get("event").isJsonPrimitive() || o.get("event").getAsString().isBlank()) {
            issues.add(where + ": needs an 'event' id, dropped");
            return null;
        }
        String event = o.get("event").getAsString();
        int weight = 1;
        if (o.has("weight")) {
            weight = number(o, "weight", -1);
            if (weight < 1) {
                issues.add(where + ": weight must be a whole number of at least 1, dropped");
                return null;
            }
        }
        int cooldownSeconds = 0;
        if (o.has("cooldown_seconds")) {
            cooldownSeconds = number(o, "cooldown_seconds", -1);
            if (cooldownSeconds < 0) {
                issues.add(where + ": cooldown_seconds must be 0 or more, dropped");
                return null;
            }
        }
        double chance = 1.0;
        if (o.has("chance")) {
            try {
                chance = o.get("chance").getAsDouble();
            } catch (RuntimeException e) {
                chance = -1;
            }
            if (chance < 0.0 || chance > 1.0) {
                issues.add(where + ": chance must be between 0 and 1, dropped");
                return null;
            }
        }
        return new Entry(event, weight, cooldownSeconds * 20, chance);
    }

    private static int number(JsonObject o, String key, int bad) {
        try {
            return o.get(key).getAsInt();
        } catch (RuntimeException e) {
            return bad;
        }
    }

    private static boolean isNumbers(JsonArray array) {
        for (JsonElement e : array) {
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return false;
        }
        return true;
    }

    private static Result fail(String source, List<String> issues, String message) {
        issues.add(source + " (file): " + message);
        return new Result(EMPTY, issues);
    }
}
