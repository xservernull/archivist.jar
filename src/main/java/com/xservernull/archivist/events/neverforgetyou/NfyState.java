// per player data for the neverforgetyou event, saved progress and the live dialogue state

package com.xservernull.archivist.events.neverforgetyou;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class NfyState {
    private NfyState() {}

    public static final class Progress {
        private final Set<Integer> collected = new TreeSet<>();
        private boolean complete;

        private final Set<Integer> messages = new TreeSet<>();

        private final Set<Integer> quotes = new TreeSet<>();

        public boolean collect(int orbIndex) { return collected.add(orbIndex); }

        public int count() { return collected.size(); }

        public Set<Integer> collected() { return Collections.unmodifiableSet(collected); }

        public boolean collectMessage(int messageIndex) { return messages.add(messageIndex); }

        public boolean collectQuote(int orbIndex) { return quotes.add(orbIndex); }

        public Set<Integer> quotes() { return Collections.unmodifiableSet(quotes); }

        public Set<Integer> messages() { return Collections.unmodifiableSet(messages); }

        public boolean isComplete() { return complete; }

        public void markComplete() { complete = true; }

        public void resetOrbs() { collected.clear(); }

        public void restore(Collection<Integer> orbs, boolean complete, Collection<Integer> messageLines, Collection<Integer> quoteOrbs) {
            collected.clear();
            collected.addAll(orbs);
            this.complete = complete;
            messages.clear();
            messages.addAll(messageLines);
            quotes.clear();
            quotes.addAll(quoteOrbs);
        }
    }

    public static final class Dialogue {
        public static final int NONE = -1;
        public static final int ENTRY = 0;

        public static final int ORB_BASE = 10;

        public static final int FINAL = 5;

        public boolean inSession;
        public int sequence = NONE;

        public int line;
        public long lastLineTime;

        public int lineDelay = 50;

        public boolean waiting;

        public String pendingChoice = "";

        public String pendingLine = "";

        public boolean answering;
        public int answerLine;
        public long answerTime;

        public String answer = "";

        public boolean entryDone;

        public boolean finished;

        public boolean bookGiven;

        public boolean messageOrbsOn;

        public int messageSpawned;

        public double messageX, messageZ;

        public void begin(int sequence, long now, int lineDelayTicks) {
            this.sequence = sequence;
            line = 0;
            lastLineTime = now - lineDelayTicks;
            lineDelay = 50;
            waiting = false;
            answering = false;
        }

        public int orbOfSequence() { return sequence >= ORB_BASE ? sequence - ORB_BASE : -1; }

        public void clear() {
            inSession = false;
            sequence = NONE;
            line = 0;
            lastLineTime = 0;
            lineDelay = 50;
            waiting = false;
            pendingChoice = "";
            pendingLine = "";
            answering = false;
            answerLine = 0;
            answerTime = 0;
            answer = "";
            entryDone = false;
            finished = false;
            bookGiven = false;
            messageOrbsOn = false;
            messageSpawned = 0;
            messageX = 0;
            messageZ = 0;
        }
    }
}