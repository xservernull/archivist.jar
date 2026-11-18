// the first quick event, and the example for new ones: the old library corruption look.

package com.xservernull.archivist.events;

import net.minecraft.server.level.ServerPlayer;

final class GreyscalePulse implements QuickEvent {
    private static final int DURATION = 200;

    @Override public String id() { return "greyscale_pulse"; }

    @Override
    public void start(ServerPlayer player) {
        EventNet.fx(player, "greyscale", DURATION);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        return age >= DURATION;
    }
}
