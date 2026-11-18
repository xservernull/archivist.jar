// parses the neverforgetyou json into lines, choices, orbs and the book using gson, bad parts
// get dropped and reported

package com.xservernull.archivist.events.neverforgetyou;

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

public record NfyScript(
        List<String> orbQuotes,
        List<Line> entry,
        List<List<Line>> orbs,
        Map<String, Choice> choices,
        List<Line> finalLines,
        List<String> messageOrbs,
        Book book) {
    public static final int ORB_COUNT = 10;

    public enum Kind { HOPE, YOU, RAW, ORB_TOAST, CHOICE }

    public record Line(Kind kind, String text, int number) {}

    public record Option(String key, String label, String button) {}

    public record Choice(String name, List<Option> options, Map<String, List<Line>> responses) {}

    public record Book(String title, String author, boolean italic, String warningPage, List<String> pages) {}

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String where, String message) {
        @Override public String toString() { return severity + " " + where + ": " + message; }
    }

    public record Result(NfyScript script, List<Issue> issues) {
        public boolean ok() { return script != null && issues.stream().noneMatch(i -> i.severity() == Severity.ERROR); }
    }

    public List<Line> orb(int n) { return orbs.get(n); }

    public Choice choice(String name) { return choices.get(name); }

    public static Result parse(String sourceName, Reader reader) {
        Run run = new Run(sourceName);
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return run.fail("the file is not a JSON object");
            root = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            return run.fail("not valid JSON: " + e.getMessage());
        }
        try {
            return compile(run, root);
        } catch (RuntimeException e) {
            return run.fail("unexpected json shape: " + e.getMessage());
        }
    }

    private static Result compile(Run run, JsonObject root) {
        List<String> quotes = run.strings(root, "orb_quotes");
        if (quotes != null && quotes.size() != ORB_COUNT) {
            run.error("orb_quotes", "needs exactly " + ORB_COUNT + " quotes, has " + quotes.size());
        }
        Map<String, Choice> choices = run.choices(root);
        List<Line> entry = run.lines(root, "entry", quotes, choices);
        List<List<Line>> orbs = run.orbs(root, quotes, choices);
        List<Line> finalLines = run.lines(root, "final_lines", quotes, choices);
        List<String> messageOrbs = run.strings(root, "message_orbs");
        Book book = run.book(root);

        if (quotes == null || entry == null || orbs == null || finalLines == null
                || messageOrbs == null || book == null || quotes.size() != ORB_COUNT) {
            return new Result(null, run.issues);
        }
        return new Result(new NfyScript(List.copyOf(quotes), List.copyOf(entry), List.copyOf(orbs),
                Map.copyOf(choices), List.copyOf(finalLines), List.copyOf(messageOrbs), book), run.issues);
    }

    private static final class Run {
        private final String source;
        private final List<Issue> issues = new ArrayList<>();

        Run(String source) { this.source = source; }

        void error(String where, String message) { issues.add(new Issue(Severity.ERROR, source + " " + where, message)); }

        Result fail(String message) {
            error("(file)", message);
            return new Result(null, issues);
        }

        JsonArray array(JsonObject o, String key) {
            JsonElement e = o.get(key);
            if (e == null || !e.isJsonArray()) {
                error(key, "missing or not a list");
                return null;
            }
            return e.getAsJsonArray();
        }

        List<String> strings(JsonObject o, String key) {
            JsonArray a = array(o, key);
            if (a == null) return null;
            List<String> out = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i).isJsonPrimitive()) out.add(a.get(i).getAsString());
                else error(key + "[" + i + "]", "not a string");
            }
            return out;
        }

        List<Line> lines(JsonObject o, String key, List<String> quotes, Map<String, Choice> choices) {
            List<String> raw = strings(o, key);
            return raw == null ? null : lines(raw, key, quotes, choices);
        }

        List<Line> lines(List<String> raw, String where, List<String> quotes, Map<String, Choice> choices) {
            List<Line> out = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                Line line = line(raw.get(i), where + "[" + i + "]", quotes, choices);
                if (line != null) out.add(line);
            }
            return out;
        }

        Line line(String raw, String where, List<String> quotes, Map<String, Choice> choices) {
            if (raw.startsWith("YOU:")) return new Line(Kind.YOU, raw.substring(4), -1);
            if (raw.startsWith("RAW:")) return new Line(Kind.RAW, raw.substring(4), -1);
            if (raw.startsWith("ORB_TOAST:")) {
                int n;
                try {
                    n = Integer.parseInt(raw.substring(10).trim());
                } catch (NumberFormatException e) {
                    error(where, "ORB_TOAST needs a number: " + raw);
                    return null;
                }
                if (quotes == null || n < 0 || n >= quotes.size()) {
                    error(where, "ORB_TOAST:" + n + " has no quote in orb_quotes");
                    return null;
                }
                return new Line(Kind.ORB_TOAST, quotes.get(n), n);
            }
            if (raw.startsWith("CHOICE:")) {
                String name = raw.substring(7).trim();
                if (!choices.containsKey(name)) {
                    error(where, "CHOICE:" + name + " is not defined under choices");
                    return null;
                }
                return new Line(Kind.CHOICE, name, -1);
            }
            return new Line(Kind.HOPE, raw, -1);
        }

        List<List<Line>> orbs(JsonObject root, List<String> quotes, Map<String, Choice> choices) {
            JsonArray a = array(root, "orbs");
            if (a == null) return null;
            if (a.size() != ORB_COUNT) {
                error("orbs", "needs exactly " + ORB_COUNT + " dialogues, has " + a.size());
                return null;
            }
            List<List<Line>> out = new ArrayList<>();
            for (int i = 0; i < a.size(); i++) {
                if (!a.get(i).isJsonArray()) {
                    error("orbs[" + i + "]", "not a list");
                    out.add(List.of());
                    continue;
                }
                List<String> raw = new ArrayList<>();
                for (JsonElement e : a.get(i).getAsJsonArray()) raw.add(e.getAsString());
                out.add(List.copyOf(lines(raw, "orbs[" + i + "]", quotes, choices)));
            }
            return out;
        }

        Map<String, Choice> choices(JsonObject root) {
            Map<String, Choice> out = new LinkedHashMap<>();
            JsonElement e = root.get("choices");
            if (e == null || !e.isJsonObject()) {
                error("choices", "missing or not an object");
                return out;
            }
            for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
                if (entry.getKey().startsWith("_")) continue;
                Choice choice = choice(entry.getKey(), entry.getValue());
                if (choice != null) out.put(entry.getKey(), choice);
            }
            return out;
        }

        Choice choice(String name, JsonElement element) {
            String where = "choices." + name;
            if (!element.isJsonObject()) {
                error(where, "not an object");
                return null;
            }
            JsonObject o = element.getAsJsonObject();
            JsonArray rawOptions = array(o, "options");
            JsonElement rawResponses = o.get("responses");
            if (rawOptions == null || rawResponses == null || !rawResponses.isJsonObject()) {
                error(where, "needs 'options' (list) and 'responses' (object)");
                return null;
            }
            List<Option> options = new ArrayList<>();
            for (JsonElement item : rawOptions) {
                JsonObject opt = item.isJsonObject() ? item.getAsJsonObject() : null;
                if (opt == null || !opt.has("key") || !opt.has("button")) {
                    error(where + ".options", "every option needs 'key' and 'button'");
                    return null;
                }
                String key = opt.get("key").getAsString();
                String label = opt.has("label") ? opt.get("label").getAsString() : key;
                options.add(new Option(key, label, opt.get("button").getAsString()));
            }
            if (options.isEmpty()) {
                error(where, "no options");
                return null;
            }
            Map<String, List<Line>> responses = new LinkedHashMap<>();
            for (Option option : options) {
                JsonElement r = rawResponses.getAsJsonObject().get(option.key());
                if (r == null || !r.isJsonArray()) {
                    error(where + ".responses", "no reply list for option '" + option.key() + "'");
                    return null;
                }
                List<String> raw = new ArrayList<>();
                for (JsonElement line : r.getAsJsonArray()) raw.add(line.getAsString());
                responses.put(option.key(), List.copyOf(lines(raw, where + ".responses." + option.key(), null, Map.of())));
            }
            return new Choice(name, List.copyOf(options), Map.copyOf(responses));
        }

        Book book(JsonObject root) {
            JsonElement e = root.get("book");
            if (e == null || !e.isJsonObject()) {
                error("book", "missing or not an object");
                return null;
            }
            JsonObject o = e.getAsJsonObject();
            List<String> pages = strings(o, "pages");
            if (pages == null || pages.isEmpty()) {
                error("book.pages", "needs at least one page");
                return null;
            }
            return new Book(text(o, "title", "hope"), text(o, "author", "hope"),
                    o.has("italic") && o.get("italic").getAsBoolean(), text(o, "warning_page", ""), List.copyOf(pages));
        }

        private static String text(JsonObject o, String key, String fallback) {
            return o.has(key) ? o.get(key).getAsString() : fallback;
        }
    }
}