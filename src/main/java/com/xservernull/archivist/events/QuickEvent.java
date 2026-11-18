// one short event for one player (rules in docs/events.md: 20 to 60 seconds, one clear player
// action, nothing permanent, safe to cancel at any time).

package com.xservernull.archivist.events;

import net.minecraft.server.level.ServerPlayer;

public interface QuickEvent {
    String id();

    void start(ServerPlayer player);

    boolean tick(ServerPlayer player, int age);

    default void stop(ServerPlayer player) {}
}
