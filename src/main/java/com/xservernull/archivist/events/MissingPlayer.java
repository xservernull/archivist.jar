// old missingplayerevent: the player turns invisible and silent for 100 to 300 ticks, so
// anyone else on the server sees nobody where they stand.

package com.xservernull.archivist.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;

final class MissingPlayer implements QuickEvent {
    private static final int MIN = 100, MAX = 300;

    @Override public String id() { return "missing_player"; }

    @Override
    public void start(ServerPlayer player) {
        player.setInvisible(true);
        player.setSilent(true);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        if (!player.isInvisible()) player.setInvisible(true);
        return age >= MAX || (age >= MIN && player.getRandom().nextInt(100) == 0);
    }

    @Override
    public void stop(ServerPlayer player) {
        player.setSilent(false);
        if (!player.hasEffect(MobEffects.INVISIBILITY)) player.setInvisible(false);
    }
}
