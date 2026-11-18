// old_nether reactor call: the weird spinning block calls for you.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

final class OldNetherReactorCall implements QuickEvent {
    private static final int DURATION = 20 * 30;

    @Override public String id() { return "old_nether_reactor_call"; }

    @Override
    public void start(ServerPlayer player) {
        BlockPos core = OldNetherReactor.beacon(player);
        if (core == null) return;
        player.sendSystemMessage(Texts.tr("archivist.event.old_nether_reactor_call.start", "it is calling you."));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.6F, 0.5F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        BlockPos core = OldNetherReactor.knownCore(player);
        if (core == null || OldNetherReactor.coreDone(player.serverLevel(), core)) return true;
        if (age % 20 == 0) {
            int meters = (int) Math.round(Math.sqrt(core.distSqr(player.blockPosition())));
            player.displayClientMessage(Component.literal("§c" + meters + "m"), true);
        }
        return age >= DURATION;
    }
}
