// reads a q&a json file with gson, validates it and compiles it into a QaBook
// bad entries get dropped and reported as issues, nothing is logged in here
package com.xservernull.archivist.qa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QaLoader {
    private QaLoader() {}

    public enum Severity { ERROR, WARNING }

    public record Issue(Severity severity, String where, String message) {
        @Override public String toString() { return severity + " " + where + ": " + message; }
    }

    public record Result(QaBook book, List<Issue> issues) {
        public boolean ok() { return book != null && errorCount() == 0; }
        public long errorCount() { return count(issues, Severity.ERROR); }
        public long warningCount() { return count(issues, Severity.WARNING); }
    }

    private static long count(List<Issue> issues, Severity s) {
        return issues.stream().filter(i -> i.severity() == s).count();
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Pattern FLAG = Pattern.compile("[a-z0-9_.:\\-]+");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");
    private static final Set<String> PLACEHOLDERS = Set.of("name");
    private static final int MAX_EXPANSIONS = 256;

    public static Result parse(String sourceName, String json) {
        return load(sourceName, new StringReader(json));
    }

    public static Result load(String sourceName, Reader reader) {
        Run run = new Run(sourceName);
        JsonObject root;
        try {
            JsonElement el = JsonParser.parseReader(reader);
            if (!el.isJsonObject()) {
                run.error("<root>", "top level must be a JSON object");
                return run.result(null);
            }
            root = el.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            run.error("<root>", "not valid JSON: " + e.getMessage());
            return run.result(null);
        }
        return run.compile(root);
    }

    private static final class Run {
        final String src;
        final List<Issue> issues = new ArrayList<>();
        Map<String, List<List<String>>> synonyms = new HashMap<>();
        Set<String> declaredStats = new HashSet<>();
        Set<String> declaredFlags = new HashSet<>();
        Set<String> flagsUsed = new LinkedHashSet<>();
        Lint lint = new Lint(true, List.of("!", "\"", "*"), 4);

        Run(String src) { this.src = src; }

        record Lint(boolean lowercase, List<String> forbid, int maxSentences) {}

        void error(String where, String msg) { issues.add(new Issue(Severity.ERROR, src + " " + where, msg)); }
        void warn(String where, String msg) { issues.add(new Issue(Severity.WARNING, src + " " + where, msg)); }
        Result result(QaBook b) { return new Result(b, issues); }

        long errors() { return count(issues, Severity.ERROR); }

        Result compile(JsonObject root) {
            checkKeys(root, Set.of("schema", "speaker", "settings", "context", "synonyms", "fallbacks", "entries"), "<root>");
            if (!root.has("schema") || !isInt(root.get("schema")) || root.get("schema").getAsInt() != 1) {
                error("schema", "must be the number 1");
                return result(null);
            }
            String speaker = str(root, "speaker", "speaker");
            if (speaker == null || speaker.isBlank()) {
                error("speaker", "required, a non-empty string");
                return result(null);
            }

            QaBook.Settings settings = parseSettings(root.get("settings"));
            parseContext(root.get("context"));
            parseSynonyms(root.get("synonyms"));

            Map<String, List<String>> fallbacks = parseFallbacks(root.get("fallbacks"));
            if (fallbacks == null) return result(null);

            if (!root.has("entries") || !root.get("entries").isJsonArray()) {
                error("entries", "required, an array");
                return result(null);
            }

            List<QaBook.Entry> entries = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            Set<String> setByEntries = new HashSet<>();
            JsonArray arr = root.getAsJsonArray("entries");
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                String where = "entries[" + i + "]";
                if (!el.isJsonObject()) { error(where, "must be an object"); continue; }
                JsonObject o = el.getAsJsonObject();
                if (o.has("id") && o.get("id").isJsonPrimitive()) where += " '" + o.get("id").getAsString() + "'";
                long before = errors();
                QaBook.Entry e = parseEntry(o, where);
                if (errors() != before || e == null) { warn(where, "entry dropped because of the errors above"); continue; }
                if (!seen.add(e.id())) { error(where, "duplicate id"); continue; }
                setByEntries.addAll(e.set());
                entries.add(e);
            }

            for (String f : flagsUsed) {
                if (!declaredFlags.contains(f) && !setByEntries.contains(f)) {
                    warn("flags", "'" + f + "' is tested but never set by any entry and not listed in context.flags (typo?)");
                }
            }
            if (entries.isEmpty()) warn("entries", "no usable entries; every turn will use fallbacks");
            return result(new QaBook(speaker, entries, fallbacks, settings));
        }

        QaBook.Settings parseSettings(JsonElement el) {
            double minMatch = 0.75;
            boolean fuzzy = true;
            int topicTurns = 3, recent = 8;
            if (el != null) {
                if (!el.isJsonObject()) {
                    error("settings", "must be an object");
                } else {
                    JsonObject o = el.getAsJsonObject();
                    checkKeys(o, Set.of("min_match", "fuzzy", "topic_turns", "recent_replies", "lint"), "settings");
                    if (o.has("min_match")) minMatch = num(o, "min_match", "settings.min_match", 0.75, 0, 100);
                    if (o.has("fuzzy")) fuzzy = bool(o, "fuzzy", "settings.fuzzy", true);
                    if (o.has("topic_turns")) topicTurns = (int) num(o, "topic_turns", "settings.topic_turns", 3, 0, 50);
                    if (o.has("recent_replies")) recent = (int) num(o, "recent_replies", "settings.recent_replies", 8, 0, 200);
                    if (o.has("lint")) parseLint(o.get("lint"));
                }
            }
            return new QaBook.Settings(minMatch, fuzzy, topicTurns, recent);
        }

        void parseLint(JsonElement el) {
            if (!el.isJsonObject()) { error("settings.lint", "must be an object"); return; }
            JsonObject o = el.getAsJsonObject();
            checkKeys(o, Set.of("lowercase", "forbid", "max_sentences"), "settings.lint");
            boolean lower = o.has("lowercase") ? bool(o, "lowercase", "settings.lint.lowercase", true) : lint.lowercase();
            List<String> forbid = o.has("forbid") ? strings(o.get("forbid"), "settings.lint.forbid") : lint.forbid();
            int max = o.has("max_sentences") ? (int) num(o, "max_sentences", "settings.lint.max_sentences", 4, 1, 100) : lint.maxSentences();
            lint = new Lint(lower, forbid, max);
        }

        void parseContext(JsonElement el) {
            if (el == null) return;
            if (!el.isJsonObject()) { error("context", "must be an object"); return; }
            JsonObject o = el.getAsJsonObject();
            checkKeys(o, Set.of("stats", "flags"), "context");
            if (o.has("stats")) for (String s : strings(o.get("stats"), "context.stats")) {
                if (!ID.matcher(s).matches()) error("context.stats", "bad stat name '" + s + "' (use a-z 0-9 _)");
                else declaredStats.add(s);
            }
            if (o.has("flags")) for (String f : strings(o.get("flags"), "context.flags")) {
                if (!FLAG.matcher(f).matches()) error("context.flags", "bad flag name '" + f + "'");
                else declaredFlags.add(f);
            }
        }

        void parseSynonyms(JsonElement el) {
            if (el == null) return;
            if (!el.isJsonObject()) { error("synonyms", "must be an object"); return; }
            for (Map.Entry<String, JsonElement> e : el.getAsJsonObject().entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                String where = "synonyms." + e.getKey();
                if (!ID.matcher(e.getKey()).matches()) { error(where, "group name must be a-z 0-9 _"); continue; }
                List<List<String>> phrases = new ArrayList<>();
                for (String s : strings(e.getValue(), where)) {
                    if (s.contains("@")) { error(where, "'" + s + "': groups can't reference other groups"); continue; }
                    List<String> t = QaBook.tokens(s, true);
                    if (t.isEmpty()) { error(where, "'" + s + "' has no usable words"); continue; }
                    if (!checkPrefixes(t, where, s)) continue;
                    phrases.add(t);
                }
                if (phrases.isEmpty()) error(where, "group is empty");
                synonyms.put(e.getKey(), phrases);
            }
        }

        Map<String, List<String>> parseFallbacks(JsonElement el) {
            if (el == null || !el.isJsonObject()) { error("fallbacks", "required, an object with at least \"normal\""); return null; }
            JsonObject o = el.getAsJsonObject();
            checkKeys(o, Set.of("normal", "quiet", "question", "statement"), "fallbacks");
            Map<String, List<String>> out = new LinkedHashMap<>();
            for (String g : List.of("normal", "quiet", "question", "statement")) {
                String where = "fallbacks." + g;
                List<String> lines = new ArrayList<>();
                for (String s : strings(o.get(g), where)) {
                    if (s.isBlank()) error(where, "blank line");
                    else { checkText(s, where); lines.add(s); }
                }
                if (!lines.isEmpty()) out.put(g, lines);
                else if (g.equals("normal")) { error(where, "required, with at least one line"); return null; }
                else if (o.has(g) || g.equals("quiet")) warn(where, "missing or empty; the 'normal' lines will be used instead");
            }
            return out;
        }

        QaBook.Entry parseEntry(JsonObject o, String where) {
            checkKeys(o, Set.of("id", "input", "priority", "weight", "once", "match", "when", "replies", "set", "clear", "topic"), where);
            String id = str(o, "id", where + ".id");
            if (id == null || !ID.matcher(id).matches()) { error(where, "id is required and must be a-z 0-9 _"); return null; }

            int priority = o.has("priority") ? (int) num(o, "priority", where + ".priority", 0, -1000, 1000) : 0;
            double weight = o.has("weight") ? num(o, "weight", where + ".weight", 1, 0.0001, 1000) : 1.0;
            boolean once = o.has("once") && bool(o, "once", where + ".once", false);

            boolean hasMatch = o.has("match");
            List<QaBook.Pattern> any = List.of(), all = List.of(), none = List.of();
            if (hasMatch) {
                if (!o.get("match").isJsonObject()) { error(where + ".match", "must be an object"); }
                else {
                    JsonObject m = o.getAsJsonObject("match");
                    checkKeys(m, Set.of("any", "all", "none"), where + ".match");
                    any = patterns(m.get("any"), where + ".match.any");
                    all = patterns(m.get("all"), where + ".match.all");
                    none = patterns(m.get("none"), where + ".match.none");
                    if (any.isEmpty() && all.isEmpty()) error(where + ".match", "needs a non-empty \"any\" or \"all\"");
                }
            }

            QaBook.InputKind kind = hasMatch ? QaBook.InputKind.MESSAGE : QaBook.InputKind.SILENT;
            if (o.has("input")) {
                String s = str(o, "input", where + ".input");
                if ("message".equals(s)) kind = QaBook.InputKind.MESSAGE;
                else if ("silent".equals(s)) kind = QaBook.InputKind.SILENT;
                else if ("any".equals(s)) kind = QaBook.InputKind.ANY;
                else error(where + ".input", "must be \"message\", \"silent\" or \"any\"");
            }
            if (kind == QaBook.InputKind.MESSAGE && !hasMatch) error(where, "input \"message\" needs a \"match\" block");
            if (kind == QaBook.InputKind.SILENT && hasMatch) error(where, "input \"silent\" can't have a \"match\" block (nothing to match)");

            QaBook.Cond when = o.has("when") ? cond(o.get("when"), where + ".when") : QaBook.Cond.NONE;

            List<QaBook.Variant> replies = new ArrayList<>();
            if (!o.has("replies") || !o.get("replies").isJsonArray() || o.getAsJsonArray("replies").isEmpty()) {
                error(where + ".replies", "required, a non-empty array");
            } else {
                JsonArray ra = o.getAsJsonArray("replies");
                for (int i = 0; i < ra.size(); i++) {
                    QaBook.Variant v = variant(ra.get(i), where + ".replies[" + i + "]");
                    if (v != null) replies.add(v);
                }
                if (!replies.isEmpty() && replies.stream().allMatch(QaBook.Variant::usesName)) {
                    warn(where + ".replies", "every reply uses {name}, which is only allowed once per player; this entry goes quiet after one use");
                }
            }

            List<String> set = o.has("set") ? flags(o.get("set"), where + ".set") : List.of();
            List<String> clear = o.has("clear") ? flags(o.get("clear"), where + ".clear") : List.of();
            String topic = null;
            if (o.has("topic")) {
                topic = str(o, "topic", where + ".topic");
                if (topic == null || !ID.matcher(topic).matches()) { error(where + ".topic", "must be a-z 0-9 _"); topic = null; }
            }

            return new QaBook.Entry(id, kind, priority, weight, any, all, none, when, replies, set, clear, topic, once);
        }

        QaBook.Variant variant(JsonElement el, String where) {
            String text;
            double weight = 1.0;
            QaBook.Cond when = QaBook.Cond.NONE;
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
                text = el.getAsString();
            } else if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                checkKeys(o, Set.of("text", "weight", "when"), where);
                text = str(o, "text", where + ".text");
                if (o.has("weight")) weight = num(o, "weight", where + ".weight", 1, 0.0001, 1000);
                if (o.has("when")) when = cond(o.get("when"), where + ".when");
            } else {
                error(where, "must be a string or an object with \"text\"");
                return null;
            }
            if (text == null || text.isBlank()) { error(where, "reply text is blank"); return null; }
            boolean usesName = false;
            Matcher pm = PLACEHOLDER.matcher(text);
            while (pm.find()) {
                if (!PLACEHOLDERS.contains(pm.group(1))) error(where, "unknown placeholder {" + pm.group(1) + "} (allowed: {name})");
                if (pm.group(1).equals("name")) usesName = true;
            }
            if (PLACEHOLDER.matcher(text).replaceAll("").matches("(?s).*[{}].*")) {
                error(where, "stray brace in reply text");
            }
            checkText(text, where);
            return new QaBook.Variant(text, weight, when, usesName);
        }

        QaBook.Cond cond(JsonElement el, String where) {
            if (!el.isJsonObject()) { error(where, "must be an object"); return QaBook.Cond.NONE; }
            JsonObject o = el.getAsJsonObject();
            checkKeys(o, Set.of("flags", "not_flags", "stats", "topic"), where);
            List<String> fl = o.has("flags") ? flags(o.get("flags"), where + ".flags") : List.of();
            List<String> nf = o.has("not_flags") ? flags(o.get("not_flags"), where + ".not_flags") : List.of();
            flagsUsed.addAll(fl);
            flagsUsed.addAll(nf);
            Map<String, QaBook.Range> stats = new LinkedHashMap<>();
            if (o.has("stats")) {
                if (!o.get("stats").isJsonObject()) error(where + ".stats", "must be an object");
                else for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stats").entrySet()) {
                    String w = where + ".stats." + e.getKey();
                    if (!declaredStats.contains(e.getKey())) { error(w, "stat not listed in context.stats (typo?)"); continue; }
                    if (!e.getValue().isJsonObject()) { error(w, "must be an object with min and/or max"); continue; }
                    JsonObject r = e.getValue().getAsJsonObject();
                    checkKeys(r, Set.of("min", "max"), w);
                    if (!r.has("min") && !r.has("max")) { error(w, "needs min and/or max"); continue; }
                    int min = r.has("min") ? (int) num(r, "min", w + ".min", Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE) : Integer.MIN_VALUE;
                    int max = r.has("max") ? (int) num(r, "max", w + ".max", Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE) : Integer.MAX_VALUE;
                    if (min > max) { error(w, "min is greater than max"); continue; }
                    stats.put(e.getKey(), new QaBook.Range(min, max));
                }
            }
            Set<String> topics = new LinkedHashSet<>();
            if (o.has("topic")) for (String t : strings(o.get("topic"), where + ".topic")) {
                if (!ID.matcher(t).matches()) error(where + ".topic", "bad topic '" + t + "'");
                else topics.add(t);
            }
            return new QaBook.Cond(new LinkedHashSet<>(fl), new LinkedHashSet<>(nf), stats, topics);
        }

        List<QaBook.Pattern> patterns(JsonElement el, String where) {
            List<QaBook.Pattern> out = new ArrayList<>();
            if (el == null) return out;
            for (String s : strings(el, where)) {
                List<String> t = QaBook.tokens(s, true);
                if (t.isEmpty()) { error(where, "'" + s + "' has no usable words"); continue; }
                if (!checkPrefixes(t, where, s)) continue;
                boolean bad = false;
                List<List<String>> variants = new ArrayList<>();
                variants.add(new ArrayList<>());
                for (String tok : t) {
                    if (tok.startsWith("@")) {
                        List<List<String>> group = synonyms.get(tok.substring(1));
                        if (group == null || group.isEmpty()) { error(where, "'" + s + "': unknown synonym group " + tok); bad = true; break; }
                        List<List<String>> next = new ArrayList<>();
                        for (List<String> base : variants) for (List<String> phrase : group) {
                            List<String> joined = new ArrayList<>(base);
                            joined.addAll(phrase);
                            next.add(joined);
                        }
                        variants = next;
                        if (variants.size() > MAX_EXPANSIONS) { error(where, "'" + s + "' expands to too many patterns"); bad = true; break; }
                    } else {
                        for (List<String> base : variants) base.add(tok);
                    }
                }
                if (bad) continue;
                for (List<String> v : variants) out.add(new QaBook.Pattern(List.copyOf(v), s));
            }
            return out;
        }

        boolean checkPrefixes(List<String> toks, String where, String raw) {
            for (String t : toks) {
                if (t.equals("@") || t.equals("*")) { error(where, "'" + raw + "': stray marker"); return false; }
                int star = t.indexOf('*');
                if (star >= 0 && (star != t.length() - 1 || t.length() < 3 || t.contains("@"))) {
                    error(where, "'" + raw + "': '*' only goes at the end of a word of 2+ letters (e.g. tenatur*)");
                    return false;
                }
                int bang = t.indexOf('!');
                if (bang >= 0 && (bang != t.length() - 1 || t.length() < 3 || t.contains("@") || t.contains("*"))) {
                    error(where, "'" + raw + "': '!' only goes at the end of a word of 2+ letters (e.g. nether!), not with '@' or '*'");
                    return false;
                }
                int at = t.lastIndexOf('@');
                if (at > 0 || (at == 0 && t.length() == 1)) { error(where, "'" + raw + "': '@' only goes at the start of a group name"); return false; }
            }
            return true;
        }

        void checkText(String text, String where) {
            if (lint.lowercase() && !text.equals(text.toLowerCase(Locale.ROOT))) {
                warn(where, "has capital letters: '" + clip(text) + "'");
            }
            for (String f : lint.forbid()) {
                if (!f.isEmpty() && text.contains(f)) warn(where, "contains forbidden '" + f + "': '" + clip(text) + "'");
            }
            String[] sentences = text.trim().split("(?<=[.?])\\s+");
            if (sentences.length > lint.maxSentences()) {
                warn(where, sentences.length + " sentences (max " + lint.maxSentences() + "): '" + clip(text) + "'");
            }
        }

        static String clip(String s) { return s.length() > 48 ? s.substring(0, 45) + "..." : s; }

        void checkKeys(JsonObject o, Set<String> known, String where) {
            for (String k : o.keySet()) {
                if (k.startsWith("_") || known.contains(k)) continue;
                warn(where, "unknown key '" + k + "' (ignored; typo?)");
            }
        }

        List<String> flags(JsonElement el, String where) {
            List<String> out = new ArrayList<>();
            for (String f : strings(el, where)) {
                if (!FLAG.matcher(f).matches()) { error(where, "bad flag name '" + f + "' (use a-z 0-9 _ . : -)"); continue; }
                if (f.startsWith("_") || f.startsWith("done:")) { error(where, "'" + f + "' is reserved for the engine"); continue; }
                out.add(f);
            }
            return out;
        }

        List<String> strings(JsonElement el, String where) {
            List<String> out = new ArrayList<>();
            if (el == null) return out;
            if (!el.isJsonArray()) { error(where, "must be an array of strings"); return out; }
            for (JsonElement e : el.getAsJsonArray()) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) out.add(e.getAsString());
                else error(where, "every item must be a string");
            }
            return out;
        }

        String str(JsonObject o, String key, String where) {
            JsonElement e = o.get(key);
            if (e == null) return null;
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) return e.getAsString();
            error(where, "must be a string");
            return null;
        }

        boolean bool(JsonObject o, String key, String where, boolean dflt) {
            JsonElement e = o.get(key);
            if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) return e.getAsBoolean();
            error(where, "must be true or false");
            return dflt;
        }

        double num(JsonObject o, String key, String where, double dflt, double lo, double hi) {
            JsonElement e = o.get(key);
            if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) { error(where, "must be a number"); return dflt; }
            double v = e.getAsDouble();
            if (Double.isNaN(v) || v < lo || v > hi) { error(where, "out of range (" + lo + " to " + hi + ")"); return dflt; }
            return v;
        }

        static boolean isInt(JsonElement e) {
            return e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && e.getAsDouble() == Math.rint(e.getAsDouble());
        }
    }
}