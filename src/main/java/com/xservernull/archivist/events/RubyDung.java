// the rd132011 (rubydung) event, the old tenatureeventhandler + terminalblockclickhandler
// rewritten (#193): the terminal: right-clicking the command block at 128, 44, 128 in

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;

public final class RubyDung {
    private RubyDung() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final BlockPos TERMINAL = new BlockPos(128, 44, 128);
    private static final ResourceLocation RD132011 = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "rd132011");
    private static final int KICK_DELAY = 60;
    private static final double REACH_SQ = 10.0 * 10.0;
    private static final double LIB_X = 22.5, LIB_Y = 61, LIB_Z = 9.5;

    private static final Component KICK = Texts.tr("archivist.msg.kick_ruby", "You have been kicked.");

    private static int kickAt = -1;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener(RubyDung::onClick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) route(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            if (kickAt >= 0 && e.getServer().getTickCount() >= kickAt) kickAll(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> kickAt = -1);
    }

    private static boolean inRd(Level level) {
        return level.dimension().location().equals(RD132011);
    }

    private static void onClick(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer player)) return;
        if (!inRd(e.getLevel()) || !e.getPos().equals(TERMINAL) || !e.getLevel().getBlockState(TERMINAL).is(Blocks.COMMAND_BLOCK)) return;
        e.setCanceled(true); // both hands fire this event: the command block screen never opens
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        MinecraftServer server = player.getServer();
        if (server == null || Data.get(server).erased) return;
        LOG.info("[rubydung] {} opened the terminal", player.getName().getString());
        RubyNet.openTerminal(player);
    }

    static void erase(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !inRd(player.level()) || player.distanceToSqr(TERMINAL.getCenter()) > REACH_SQ) return;
        Data data = Data.get(server);
        if (data.erased) return;
        data.erased = true;
        data.setDirty();
        kickAt = server.getTickCount() + KICK_DELAY;
        LOG.info("[rubydung] {} erased safetyprotocol.bat: the world is corrupted, kicking everyone in {} ticks",
                player.getName().getString(), KICK_DELAY);
    }

    private static void kickAll(MinecraftServer server) {
        kickAt = -1;
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            LOG.info("[safetyprotocol.bat]: {} was kicked.", player.getName().getString());
            player.connection.disconnect(KICK);
        }
    }

    private static void route(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !Data.get(server).erased || Events.memory(player).hasFlag(LibraryCorruption.DONE)) return;
        ServerLevel library = server.getLevel(Archivist.LIBRARY);
        if (library == null) {
            LOG.warn("[rubydung] library dimension not found, {} is not routed", player.getName().getString());
            return;
        }
        Warp.place(player, library, LIB_X, LIB_Y, LIB_Z);
        if (player.level() != library) {
            LOG.warn("[rubydung] could not move {} to the library", player.getName().getString());
            return;
        }
        // after the teleport: a dimension change stops the running event, so it starts last
        Events.start(player, LibraryCorruption.ID);
        LOG.info("[rubydung] {} routed to the library", player.getName().getString());
    }

    private static final class Data extends SavedData {
        private static final String NAME = "archivist_rubydung";
        boolean erased;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.erased = tag.getBoolean("erased");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("erased", erased);
            return tag;
        }
    }
}
