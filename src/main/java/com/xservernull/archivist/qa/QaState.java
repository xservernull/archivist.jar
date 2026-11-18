// per player memory for the q&a engine, keeps flags, the current topic and the last few replies
package com.xservernull.archivist.qa;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

public final class QaState {
    final Set<String> flags = new LinkedHashSet<>();
    final Deque<String> recent = new ArrayDeque<>();
    String topic;
    int topicTurnsLeft;

    public Set<String> flags() { return Collections.unmodifiableSet(flags); }
    public Collection<String> recentReplies() { return Collections.unmodifiableCollection(recent); }

    public String topic() { return topic; }
    public int topicTurnsLeft() { return topicTurnsLeft; }

    public void restore(Collection<String> flags, Collection<String> recent, String topic, int topicTurnsLeft) {
        this.flags.clear();
        if (flags != null) this.flags.addAll(flags);
        this.recent.clear();
        if (recent != null) this.recent.addAll(recent);
        startTopic(topic, topicTurnsLeft);
    }

    void startTopic(String newTopic, int turns) {
        topic = newTopic != null && turns > 0 ? newTopic : null;
        topicTurnsLeft = topic == null ? 0 : turns;
    }

    void tickTopic() {
        if (topicTurnsLeft > 0 && --topicTurnsLeft == 0) topic = null;
    }

    void remember(String template, int cap) {
        recent.remove(template);
        recent.addLast(template);
        while (recent.size() > cap) recent.removeFirst();
    }
}
