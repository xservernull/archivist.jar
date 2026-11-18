// the prayer, in every dimension at once: every #period ticks (15 minutes) the sky goes red
// and the dimension music stops for #duration ticks (1 minute) for all players together,

package com.xservernull.archivist.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class GlobalPrayer {
    private GlobalPrayer() {}

    public static final int PERIOD = 18000, DURATION = 1200;

    public static int remaining(MinecraftServer server) {
        long phase = server.overworld().getGameTime() % PERIOD;
        return phase >= PERIOD - DURATION ? (int) (PERIOD - phase) : 0;
    }

    public static boolean active(MinecraftServer server) {
        return remaining(server) > 0;
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            MinecraftServer server = e.getServer();
            if (server.overworld().getGameTime() % PERIOD != PERIOD - DURATION) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) EventNet.fx(player, "prayer", DURATION);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> catchUp(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> catchUp(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> catchUp(e.getEntity()));
    }

    private static void catchUp(net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        int left = remaining(serverPlayer.server);
        if (left > 0) EventNet.fx(serverPlayer, "prayer", left);
    }
}
