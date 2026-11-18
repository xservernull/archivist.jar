// rules of archivist:infdev20100227 (old events/infdev/, #190), and the one place that knows
// which levels are infdev (#isinfdev).

package com.xservernull.archivist.events;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

final class InfdevWorld {
    private InfdevWorld() {}

    static final String FIRST_VISIT = "infdev_first_visit";

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("archivist", "infdev20100227");

    private static final int GLASS = 99, PLANKS = 99;
    private static final int MAX_COUNT = 99;

    static boolean isInfdev(Level level) {
        return level.dimension().location().equals(ID);
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && e.getTo().location().equals(ID)) firstVisit(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && isInfdev(player.level())) firstVisit(player);
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent e) -> {
            if (e.getLevel() instanceof Level level && isInfdev(level)) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent e) -> {
            if (!(e.getEntity() instanceof Player) && isInfdev(e.getEntity().level())) e.getDrops().clear();
        });
    }

    private static void firstVisit(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        if (memory.hasFlag(FIRST_VISIT)) return;
        memory.setFlag(FIRST_VISIT);
        give(player, Items.GLASS, GLASS, 0);
        give(player, Items.OAK_PLANKS, PLANKS, 1);
    }

    private static void give(ServerPlayer player, net.minecraft.world.item.Item item, int count, int preferred) {
        ItemStack stack = stack(item, count);
        var inventory = player.getInventory();
        int slot = inventory.items.get(preferred).isEmpty() ? preferred : inventory.getFreeSlot();
        if (slot >= 0) inventory.items.set(slot, stack);
        else player.drop(stack, false);
    }

    private static ItemStack stack(net.minecraft.world.item.Item item, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, MAX_COUNT);
        return stack;
    }
}