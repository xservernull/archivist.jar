// this file sends nether portals in the overworld to old_nether and runs its rules (no
// placing, no digging down, respawn inside).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.slf4j.Logger;

final class OldNetherEntry {
    private OldNetherEntry() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceKey<Level> OLD_NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "old_nether"));

    private static final String DIED_FLAG = "old_nether_died";

    static void listen() {
        NeoForge.EVENT_BUS.addListener(OldNetherEntry::onPortal);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, OldNetherEntry::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, OldNetherEntry::onRespawn);
        NeoForge.EVENT_BUS.addListener(OldNetherEntry::onPlace);
        NeoForge.EVENT_BUS.addListener(OldNetherEntry::onBreak);
        NeoForge.EVENT_BUS.addListener(OldNetherEntry::onPortalTick);
    }

    private static final String PORTAL_SINCE = "tmp_old_nether_portal";

    private static void onPortalTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) return;
        if (!player.isAlive() || player.isSpectator() || !player.level().dimension().equals(Level.OVERWORLD)) return;
        EventState.Memory memory = Events.memory(player);
        long since = memory.cooldownUntil(PORTAL_SINCE);
        if (!player.level().getBlockState(player.blockPosition()).is(Blocks.NETHER_PORTAL)) {
            if (since != 0L) memory.setCooldown(PORTAL_SINCE, 0L);
            return;
        }
        long now = player.level().getGameTime();
        if (since == 0L) {
            memory.setCooldown(PORTAL_SINCE, now);
        } else if (now - since >= (player.isCreative() ? 10 : 90)) {
            memory.setCooldown(PORTAL_SINCE, 0L);
            LOG.info("[old_nether] {} stood in a nether portal, sending to old_nether", player.getName().getString());
            OldNetherReactor.enter(player);
        }
    }

    private static void onPortal(EntityTravelToDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!e.getDimension().equals(Level.NETHER) || !player.level().dimension().equals(Level.OVERWORLD)) return;
        e.setCanceled(true);
        LOG.info("[old_nether] {} used a nether portal, sending to old_nether", player.getName().getString());
        OldNetherReactor.enter(player);
    }

    private static boolean restricted(Entity entity) {
        return entity instanceof ServerPlayer player
                && player.level().dimension().equals(OLD_NETHER)
                && !player.isCreative() && !player.isSpectator();
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!restricted(e.getEntity())) return;
        e.setCanceled(true);
        ((ServerPlayer) e.getEntity()).sendSystemMessage(Texts.tr("archivist.msg.old_nether_place", "not like that"));
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        ServerPlayer player = e.getPlayer() instanceof ServerPlayer p ? p : null;
        if (player == null || !restricted(player)) return;
        if (e.getPos().getY() >= player.blockPosition().getY()) return;
        e.setCanceled(true);
        player.sendSystemMessage(Texts.tr("archivist.msg.old_nether_down", "there's nothing down there"));
    }

        private static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && player.level().dimension().equals(OLD_NETHER)) {
            Events.memory(player).setFlag(DIED_FLAG);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        EventState.Memory memory = Events.memory(player);
        if (!memory.hasFlag(DIED_FLAG)) return;
        memory.clearFlag(DIED_FLAG);
        MinecraftServer server = player.getServer();
        if (server == null) return;
        OldNetherReactor.enter(player);
    }
}