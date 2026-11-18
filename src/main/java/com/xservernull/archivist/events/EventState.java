// per-player data of the events framework. plain java, no minecraft imports; events registers
// one attachment for each part.

package com.xservernull.archivist.events;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class EventState {
    private EventState() {}

    public static final class Memory {
        private final Map<String, Long> cooldownUntil = new HashMap<>();
        private final Set<String> seen = new HashSet<>();
        private final Set<String> flags = new HashSet<>();

        public long cooldownUntil(String eventId) { return cooldownUntil.getOrDefault(eventId, 0L); }

        public void setCooldown(String eventId, long untilGameTime) { cooldownUntil.put(eventId, untilGameTime); }

        public Map<String, Long> cooldowns() { return Collections.unmodifiableMap(cooldownUntil); }

        public void markSeen(String eventId) { seen.add(eventId); }

        public boolean hasSeen(String eventId) { return seen.contains(eventId); }

        public Set<String> seen() { return Collections.unmodifiableSet(seen); }

        public void setFlag(String flag) { flags.add(flag); }

        public void clearFlag(String flag) { flags.remove(flag); }

        public boolean hasFlag(String flag) { return flags.contains(flag); }

        public Set<String> flags() { return Collections.unmodifiableSet(flags); }

        public void restore(Map<String, Long> cooldowns, Collection<String> seenIds, Collection<String> flagNames) {
            cooldownUntil.clear();
            cooldownUntil.putAll(cooldowns);
            seen.clear();
            seen.addAll(seenIds);
            flags.clear();
            flags.addAll(flagNames);
        }
    }

    public static final class Running {
        public String id;
        public int age;
        public long nextRoll;
    }
}
