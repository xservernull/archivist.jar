// how the player gets the admin panel item and the comehome photo, the old rules again: on
// every login and every respawn, a player with no panel in the inventory or the off hand gets

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

final class AdminPanelGrant {
    private AdminPanelGrant() {}

    static final String GIVEN = "adminpanel_given";
    static final String COMEHOME_GIVEN = "comehome_given";

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerWakeUpEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            EventState.Memory memory = Events.memory(player);
            if (!memory.hasFlag(COMEHOME_GIVEN)) {
                memory.setFlag(COMEHOME_GIVEN);
                give(player, new ItemStack(ModItems.COMEHOME.get()), "the photo");
            }
            if (memory.hasFlag(GIVEN)) return;
            memory.setFlag(GIVEN);
            ensure(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) ensure(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) ensure(player);
        });
    }

    private static boolean has(ServerPlayer player) {
        return player.getInventory().items.stream().anyMatch(stack -> stack.is(ModItems.ADMIN_PANEL.get()))
                || player.getInventory().offhand.stream().anyMatch(stack -> stack.is(ModItems.ADMIN_PANEL.get()));
    }

    private static void ensure(ServerPlayer player) {
        if (has(player)) return;
        ItemStack panel = new ItemStack(ModItems.ADMIN_PANEL.get());
        boolean added = player.getInventory().add(panel);
        if (!added || !panel.isEmpty()) {
            player.drop(panel, false);
            player.sendSystemMessage(Texts.tr("archivist.msg.panel_dropped",
                    "§e[Archivist]: your inventory was full — dropped the admin panel at your feet."));
        }
    }

    private static void give(ServerPlayer player, ItemStack stack, String what) {
        boolean added = player.getInventory().add(stack);
        if (!added || !stack.isEmpty()) {
            player.drop(stack, false);
            player.sendSystemMessage(Texts.tr("archivist.msg.photo_dropped",
                    "§e[Archivist]: your inventory was full — dropped " + what + " at your feet."));
        }
    }
}
