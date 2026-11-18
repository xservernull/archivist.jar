// compiled question and answer book for one speaker, picks a reply from the player's words using match rules, flags and topics
// immutable so it can be shared between players, all per player memory lives in QaState
package com.xservernull.archivist.qa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

public final class QaBook {
    enum InputKind { SILENT, MESSAGE, ANY }

    record Pattern(List<String> tokens, String source) {}

    record Range(int min, int max) {
        boolean contains(int v) { return v >= min && v <= max; }
    }

    record Cond(Set<String> flags, Set<String> notFlags, Map<String, Range> stats, Set<String> topics) {
        static final Cond NONE = new Cond(Set.of(), Set.of(), Map.of(), Set.of());

        boolean test(QaInput in, QaState st) {
            for (String f : flags) if (!in.flags.contains(f) && !st.flags.contains(f)) return false;
            for (String f : notFlags) if (in.flags.contains(f) || st.flags.contains(f)) return false;
            for (Map.Entry<String, Range> e : stats.entrySet()) {
                Integer v = in.stats.get(e.getKey());
                if (v == null || !e.getValue().contains(v)) return false;
            }
            if (!topics.isEmpty()) {
                String t = st.topic();
                if (t == null || !topics.contains(t)) return false;
            }
            return true;
        }

        int specificity() {
            return flags.size() + notFlags.size() + stats.size() + (topics.isEmpty() ? 0 : 1);
        }
    }

    record Variant(String text, double weight, Cond when, boolean usesName) {}

    record Entry(String id, InputKind input, int priority, double weight,
                 List<Pattern> any, List<Pattern> all, List<Pattern> none, Cond when,
                 List<Variant> replies, List<String> set, List<String> clear, String topic, boolean once) {
        boolean hasMatch() { return !any.isEmpty() || !all.isEmpty(); }
    }

    record Settings(double minMatch, boolean fuzzy, int topicTurns, int recentReplies) {}

    public record Reply(String text, String entryId, boolean fallback) {}

    public record Candidate(String entryId, int priority, double score) {}

    private record Ranked(Entry entry, double score, List<Variant> variants) {}

    private final String speaker;
    private final List<Entry> entries;
    private final Map<String, List<String>> fallbacks;
    private final Settings settings;

    private final Set<String> nameTokens;

    QaBook(String speaker, List<Entry> entries, Map<String, List<String>> fallbacks, Settings settings) {
        this.speaker = speaker;
        this.entries = List.copyOf(entries);
        Map<String, List<String>> copy = new java.util.LinkedHashMap<>();
        fallbacks.forEach((k, v) -> copy.put(k, List.copyOf(v)));
        this.fallbacks = Collections.unmodifiableMap(copy);
        this.settings = settings;
        this.nameTokens = Set.copyOf(tokens(speaker, false));
    }

    public String speaker() { return speaker; }
    public int entryCount() { return entries.size(); }

    public Reply respond(QaInput in, QaState st, Random rnd) {
        List<Ranked> ranked = rank(in, st);
        if (ranked.isEmpty()) {
            st.tickTopic();
            return fallbackLine(fallbackGroup(in), in, st, rnd);
        }
        Ranked top = ranked.get(0);
        int ties = 1;
        while (ties < ranked.size() && sameRank(top, ranked.get(ties))) ties++;
        Ranked pick = ranked.get(rnd.nextInt(ties));
        Variant v = pickWeighted(pick.variants, rnd);
        Entry e = pick.entry;
        st.flags.addAll(e.set());
        st.flags.removeAll(e.clear());
        if (e.once()) st.flags.add("done:" + e.id());
        if (v.usesName()) st.flags.add("_name_used");
        st.remember(v.text(), settings.recentReplies());
        if (e.topic() != null) st.startTopic(e.topic(), settings.topicTurns());
        else st.tickTopic();
        return new Reply(render(v.text(), in), e.id(), false);
    }

    public Reply quiet(QaInput in, QaState st, Random rnd) {
        return fallbackLine("quiet", in, st, rnd);
    }

    public List<Candidate> explain(QaInput in, QaState st) {
        List<Candidate> out = new ArrayList<>();
        for (Ranked r : rank(in, st)) out.add(new Candidate(r.entry.id(), r.entry.priority(), r.score));
        return out;
    }

    public String fallbackGroup(QaInput in) {
        if (!in.hasMessage()) return "normal";
        if (in.message.indexOf('?') >= 0) return "question";
        List<String> t = withoutOwnName(tokens(in.message, false));
        if (t.isEmpty()) return "statement";
        String first = t.get(0);
        boolean aux = AUXILIARIES.contains(first);
        if (!aux && !QUESTION_WORDS.contains(first)) return "statement";

        if (aux && t.size() > 1 && t.get(1).equals("not")) return "statement";
        return "question";
    }

    private static final Set<String> AUXILIARIES = Set.of(
            "do", "does", "did", "can", "could", "would", "will", "should", "is", "are", "am", "have", "has");

    private static final Set<String> QUESTION_WORDS = Set.of(
            "what", "why", "how", "who", "where", "when", "which", "tell", "explain", "describe", "show");

    private List<String> withoutOwnName(List<String> tokens) {
        if (nameTokens.isEmpty()) return tokens;
        List<String> out = new ArrayList<>(tokens.size());
        for (String t : tokens) if (!nameTokens.contains(t)) out.add(t);
        return out.isEmpty() ? tokens : out;
    }

    private static boolean sameRank(Ranked a, Ranked b) {
        return a.entry.priority() == b.entry.priority() && Math.abs(a.score - b.score) < 1e-9;
    }

    private List<Ranked> rank(QaInput in, QaState st) {
        boolean msg = in.hasMessage();
        List<String> mt = msg ? withoutOwnName(tokens(in.message, false)) : List.of();
        List<Ranked> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.once() && st.flags.contains("done:" + e.id())) continue;
            if (msg && e.input() == InputKind.SILENT) continue;
            if (!msg && e.input() == InputKind.MESSAGE) continue;
            if (!e.when().test(in, st)) continue;
            double matchValue = 0;
            if (msg && e.hasMatch()) {
                matchValue = matchScore(e, mt);
                if (matchValue < 0) continue;
            }
            List<Variant> variants = eligible(e.replies(), in, st);
            if (variants.isEmpty()) continue;
            double score = e.weight() + matchValue + Math.min(2.0, 0.5 * e.when().specificity());
            out.add(new Ranked(e, score, variants));
        }
        out.sort(Comparator.comparingInt((Ranked r) -> -r.entry.priority()).thenComparingDouble(r -> -r.score));
        return out;
    }

    private double matchScore(Entry e, List<String> mt) {
        double total = 0;
        for (Pattern p : e.all()) {
            double h = hit(p, mt);
            if (h <= 0) return -1;
            total += h;
        }
        if (!e.any().isEmpty()) {
            double best = 0;
            for (Pattern p : e.any()) best = Math.max(best, hit(p, mt));
            if (best <= 0) return -1;
            total += best;
        }
        for (Pattern p : e.none()) if (hit(p, mt) > 0) return -1;
        return total >= settings.minMatch() ? total : -1;
    }

    private double hit(Pattern p, List<String> mt) {
        int n = p.tokens().size();
        if (n == 0 || mt.size() < n) return 0;
        double fuzzyValue = 0;
        for (int i = 0; i + n <= mt.size(); i++) {
            boolean ok = true;
            boolean exact = true;
            for (int j = 0; j < n; j++) {
                int q = tokenMatch(p.tokens().get(j), mt.get(i + j), settings.fuzzy());
                if (q == 0) { ok = false; break; }
                if (q == 1) exact = false;
            }
            if (ok) {
                if (exact) return n;
                fuzzyValue = n * 0.8;
            }
        }
        return fuzzyValue;
    }

    private List<Variant> eligible(List<Variant> all, QaInput in, QaState st) {
        List<Variant> ok = new ArrayList<>();
        for (Variant v : all) {
            if (!v.when().test(in, st)) continue;
            if (v.usesName() && (in.playerName == null || in.playerName.isBlank() || st.flags.contains("_name_used"))) continue;
            ok.add(v);
        }
        return preferFresh(ok, Variant::text, st);
    }

    private static <T> List<T> preferFresh(List<T> items, Function<T, String> text, QaState st) {
        List<T> fresh = new ArrayList<>();
        for (T t : items) if (!st.recent.contains(text.apply(t))) fresh.add(t);
        if (!fresh.isEmpty()) return fresh;
        String last = st.recent.peekLast();
        List<T> notLast = new ArrayList<>();
        for (T t : items) if (!text.apply(t).equals(last)) notLast.add(t);
        return notLast.isEmpty() ? items : notLast;
    }

    private static Variant pickWeighted(List<Variant> vs, Random rnd) {
        double sum = 0;
        for (Variant v : vs) sum += v.weight();
        double roll = rnd.nextDouble() * sum;
        for (Variant v : vs) {
            roll -= v.weight();
            if (roll < 0) return v;
        }
        return vs.get(vs.size() - 1);
    }

    private Reply fallbackLine(String group, QaInput in, QaState st, Random rnd) {
        List<String> lines = fallbacks.getOrDefault(group, fallbacks.get("normal"));
        List<String> pool = preferFresh(lines, Function.identity(), st);
        String text = pool.get(rnd.nextInt(pool.size()));
        st.remember(text, settings.recentReplies());
        return new Reply(render(text, in), null, true);
    }

    private static String render(String template, QaInput in) {
        String name = in.playerName == null ? "" : in.playerName.trim();
        return template.replace("{name}", name);
    }

    private static final Map<String, String> CONTRACTIONS = Map.ofEntries(
            Map.entry("whats", "what is"), Map.entry("wheres", "where is"),
            Map.entry("whos", "who is"), Map.entry("hows", "how is"),
            Map.entry("thats", "that is"), Map.entry("theres", "there is"),
            Map.entry("im", "i am"), Map.entry("ive", "i have"),
            Map.entry("youre", "you are"), Map.entry("dont", "do not"),
            Map.entry("cant", "can not"), Map.entry("cannot", "can not"),
            Map.entry("wont", "will not"), Map.entry("isnt", "is not"),
            Map.entry("arent", "are not"), Map.entry("didnt", "did not"),
            Map.entry("doesnt", "does not"),
            Map.entry("u", "you"), Map.entry("ur", "your"), Map.entry("r", "are"),
            Map.entry("wat", "what"), Map.entry("wut", "what"), Map.entry("hwo", "how"));

    private static final java.util.regex.Pattern MARKS = java.util.regex.Pattern.compile("\\p{M}+");
    private static final int MAX_TEXT = 512;

    static List<String> tokens(String text, boolean keepMarkers) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        if (text.length() > MAX_TEXT) text = text.substring(0, MAX_TEXT);
        String s = text.toLowerCase(Locale.ROOT).replace("'", "").replace("\u2019", "").replace("`", "");

        s = MARKS.matcher(java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)).replaceAll("");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean keep = Character.isLetterOrDigit(c) || (keepMarkers && (c == '@' || c == '*' || c == '!'));
            if (keep) {
                cur.append(c);
            } else if (cur.length() > 0) {
                flush(cur, out);
            }
        }
        if (cur.length() > 0) flush(cur, out);
        return out;
    }

    private static void flush(StringBuilder cur, List<String> out) {
        String tok = cur.toString();
        cur.setLength(0);
        String expanded = CONTRACTIONS.get(tok);
        if (expanded == null) {
            out.add(tok);
        } else {
            for (String part : expanded.split(" ")) out.add(part);
        }
    }

    static int tokenMatch(String pattern, String token, boolean fuzzy) {
        if (pattern.endsWith("!")) {
            return token.equals(pattern.substring(0, pattern.length() - 1)) ? 2 : 0;
        }
        if (pattern.endsWith("*")) {
            return token.startsWith(pattern.substring(0, pattern.length() - 1)) ? 2 : 0;
        }
        if (pattern.equals(token)) return 2;

        if (fuzzy && pattern.length() >= 6 && token.length() >= 5) {
            int max = pattern.length() >= 9 ? 2 : 1;
            if (Math.abs(pattern.length() - token.length()) <= max
                    && distance(pattern, token, max) <= max) {
                return 1;
            }
        }
        return 0;
    }

    static int distance(String a, String b, int max) {
        int n = a.length(), m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) return max + 1;
        }
        return d[n][m];
    }
}