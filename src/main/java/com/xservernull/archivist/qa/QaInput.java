// small holder for everything known about one chat turn, like the message, player name, stats and flags
package com.xservernull.archivist.qa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class QaInput {
    String message;
    String playerName;
    final Map<String, Integer> stats = new HashMap<>();
    final Set<String> flags = new HashSet<>();

    public QaInput message(String message) { this.message = message; return this; }
    public QaInput playerName(String name) { this.playerName = name; return this; }

    public QaInput stat(String name, int value) { stats.put(name, value); return this; }

    public QaInput flag(String name) { flags.add(name); return this; }

    boolean hasMessage() { return message != null && !message.isBlank(); }
}
