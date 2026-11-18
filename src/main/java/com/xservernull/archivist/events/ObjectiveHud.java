// the grey objective text in the top left (client.cornerhud), old objectivehuddisplay.

package com.xservernull.archivist.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

final class ObjectiveHud {
    private ObjectiveHud() {}

    static final String TEXT = "archivist.objective.beat_dragon|Beat the Ender Dragon - Find end_portal using detector map";
    private static final String CLEARED = "objective_cleared";

    static void show(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        boolean on = memory.hasFlag(LibraryCorruption.DONE) && !memory.hasFlag(CLEARED);
        EventNet.objective(player, on ? TEXT : "");
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) show(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) show(player);
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (!(e.getEntity() instanceof EnderDragon dragon) || !(dragon.level() instanceof ServerLevel level)) return;
            if (level.dimension() != Level.END) return;
            for (ServerPlayer player : level.players()) {
                Events.memory(player).setFlag(CLEARED);
                show(player);
            }
        });
    }
}
