// chat blocklist matcher that handles leetspeak, spread out letters and wildcards before checking terms
package com.xservernull.archivist.qa;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WordFilter {
    public enum MatchType { WORD, PHRASE }

    public record Term(String category, String text, MatchType type) {}

    public record Result(WordFilter filter, List<String> problems) {}

    private static final WordFilter EMPTY = new WordFilter(List.of());

    private static final int SQUASH_MIN_LETTERS = 7;

    private static final int MAX_SPREAD_RUN = 24;
    private static final int MIN_SPREAD_LETTERS = 3;

    private record Compiled(Term term, String text, String squashed, String skeleton) {}

    private final List<Compiled> compiled;

    private WordFilter(List<Compiled> compiled) { this.compiled = compiled; }

    public static WordFilter empty() { return EMPTY; }

    public static WordFilter of(Collection<Term> terms) {
        List<Compiled> out = new ArrayList<>();
        for (Term t : terms) {
            String text = String.join(" ", words(fold(t.text())));
            if (text.isEmpty()) continue;
            out.add(new Compiled(t, text, text.replace(" ", ""), skeleton(text)));
        }
        return out.isEmpty() ? EMPTY : new WordFilter(List.copyOf(out));
    }

    public int size() { return compiled.size(); }

    public List<Term> terms() { return compiled.stream().map(Compiled::term).toList(); }

    public boolean blocks(String message) { return find(message) != null; }

    public Term find(String message) {
        if (message == null || compiled.isEmpty()) return null;
        if (message.length() > 512) message = message.substring(0, 512);
        List<String> tokens = tokens(message);
        if (tokens.isEmpty()) return null;
        String spaced = String.join(" ", tokens);
        String padded = " " + spaced + " ";
        String squashed = spaced.replace(" ", "");
        Set<String> spread = spreadLetters(tokens);

        for (Compiled c : compiled) {
            boolean oneWord = c.text().indexOf(' ') < 0;
            boolean hit;
            if (c.term().type() == MatchType.PHRASE) {
                hit = spaced.contains(c.text())
                        || (c.squashed().length() >= SQUASH_MIN_LETTERS && squashed.contains(c.squashed()))
                        || (oneWord && spread.contains(c.text()));
            } else if (!oneWord) {
                hit = padded.contains(" " + c.text() + " ");
            } else {
                hit = spread.contains(c.text()) || anyTokenIs(tokens, c);
            }
            if (hit) return c.term();
        }
        return null;
    }

    public static Result parse(String sourceName, String json) {
        List<String> problems = new ArrayList<>();
        List<Term> terms = new ArrayList<>();

        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            problems.add(sourceName + ": not valid JSON: " + e.getMessage());
            return new Result(EMPTY, problems);
        }
        if (!root.isJsonObject()) {
            problems.add(sourceName + ": top level must be an object of categories");
            return new Result(EMPTY, problems);
        }

        for (Map.Entry<String, JsonElement> category : root.getAsJsonObject().entrySet()) {
            String name = category.getKey();
            if (name.startsWith("_")) continue;
            if (!category.getValue().isJsonArray()) {
                problems.add(sourceName + ": category '" + name + "' must be an array");
                continue;
            }
            int index = 0;
            for (JsonElement element : category.getValue().getAsJsonArray()) {
                String where = sourceName + ": " + name + "[" + index++ + "]";
                Term term = parseTerm(name, element, where, problems);
                if (term != null) terms.add(term);
            }
        }
        return new Result(of(terms), problems);
    }

    private static Term parseTerm(String category, JsonElement element, String where, List<String> problems) {
        if (!element.isJsonObject()) {
            problems.add(where + ": must be an object");
            return null;
        }
        JsonObject o = element.getAsJsonObject();
        String text = stringOrNull(o.get("term"));
        String type = stringOrNull(o.get("matchType"));
        if (text == null || text.isBlank()) {
            problems.add(where + ": missing \"term\"");
            return null;
        }
        if (type == null) {
            problems.add(where + " '" + text + "': missing \"matchType\" (WORD or PHRASE)");
            return null;
        }
        try {
            return new Term(category, text, MatchType.valueOf(type.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            problems.add(where + " '" + text + "': matchType '" + type + "' is not WORD or PHRASE");
            return null;
        }
    }

    private static String stringOrNull(JsonElement e) {
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static String fold(String s) {
        String decomposed = Normalizer.normalize(s, Normalizer.Form.NFKD);
        StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (c == '\'' || c == '\u2019' || c == '`') continue;
            sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static List<String> words(String folded) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i <= folded.length(); i++) {
            char c = i < folded.length() ? folded.charAt(i) : ' ';
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                cur.append(c);
            } else if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        return out;
    }

    private static List<String> tokens(String message) {
        char[] c = fold(message).toCharArray();
        leet(c);
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i <= c.length; i++) {
            char ch = i < c.length ? c[i] : ' ';
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '*') {
                cur.append(ch);
            } else if (cur.length() > 0) {
                String token = stripStars(cur.toString());
                if (!token.isEmpty()) out.add(token);
                cur.setLength(0);
            }
        }
        return out;
    }

    private static String stripStars(String s) {
        int a = 0, b = s.length();
        while (a < b && s.charAt(a) == '*') a++;
        while (b > a && s.charAt(b - 1) == '*') b--;
        return s.substring(a, b);
    }

    private static boolean isLetter(char c) { return c >= 'a' && c <= 'z'; }

    private static char leetFor(char c) {
        return switch (c) {
            case '0' -> 'o';
            case '1', '!' -> 'i';
            case '3' -> 'e';
            case '4', '@' -> 'a';
            case '5', '$' -> 's';
            case '7', '+' -> 't';
            default -> 0;
        };
    }

    private static void leet(char[] c) {
        for (int pass = 0; pass < 3; pass++) {
            boolean changed = false;
            for (int i = 0; i < c.length; i++) {
                char mapped = leetFor(c[i]);
                if (mapped == 0) continue;
                boolean left = i > 0 && isLetter(c[i - 1]);
                boolean right = i + 1 < c.length && isLetter(c[i + 1]);
                if (c[i] == '!' ? left && right : left || right) {
                    c[i] = mapped;
                    changed = true;
                }
            }
            if (!changed) break;
        }
    }

    private static boolean anyTokenIs(List<String> tokens, Compiled c) {
        for (String token : tokens) {
            if (token.equals(c.text()) || wildcardMatch(token, c.text())
                    || (hasRunOfThree(token) && skeleton(token).equals(c.skeleton()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean wildcardMatch(String token, String term) {
        if (token.indexOf('*') < 0 || token.length() != term.length()) return false;
        int real = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '*') continue;
            if (c != term.charAt(i)) return false;
            real++;
        }
        return real >= 2;
    }

    private static boolean hasRunOfThree(String s) {
        for (int i = 2; i < s.length(); i++) {
            if (s.charAt(i) == s.charAt(i - 1) && s.charAt(i) == s.charAt(i - 2)) return true;
        }
        return false;
    }

    private static String skeleton(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (i == 0 || s.charAt(i) != s.charAt(i - 1)) sb.append(s.charAt(i));
        }
        return sb.toString();
    }

    private static Set<String> spreadLetters(List<String> tokens) {
        Set<String> out = new LinkedHashSet<>();
        int i = 0;
        while (i < tokens.size()) {
            if (!isSingleLetter(tokens.get(i))) { i++; continue; }
            int end = i;
            while (end < tokens.size() && isSingleLetter(tokens.get(end)) && end - i < MAX_SPREAD_RUN) end++;
            for (int a = i; a < end; a++) {
                StringBuilder sb = new StringBuilder();
                for (int b = a; b < end; b++) {
                    sb.append(tokens.get(b));
                    if (b - a + 1 >= MIN_SPREAD_LETTERS) out.add(sb.toString());
                }
            }
            i = end;
        }
        return out;
    }

    private static boolean isSingleLetter(String t) { return t.length() == 1 && isLetter(t.charAt(0)); }
}
