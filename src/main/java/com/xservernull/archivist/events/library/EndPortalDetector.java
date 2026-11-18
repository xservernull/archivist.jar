// tells the client where the end portal is while a player holds the detector map in the
// overworld finds the stronghold once per server, then scans around it for the real portal

package com.xservernull.archivist.events.library;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.events.EventNet;
import com.xservernull.archivist.events.EventState;
import com.xservernull.archivist.events.Events;
import com.xservernull.archivist.item.DetectorMapItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.List;

public final class EndPortalDetector {
    private EndPortalDetector() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final String EXACT = "end_portal:", APPROX = "end_stronghold:", RETRY = "end_portal_retry";
    private static final int SEND_EVERY = 20, SEARCH_CHUNKS = 100, ROOM = 12;
    private static final int RESCAN_TICKS = 100, NOT_FOUND_TICKS = 400;

    private static BlockPos spawnStronghold;
    private static boolean searched;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            spawnStronghold = null;
            searched = false;
        });
    }

    private static void tick(ServerPlayer player) {
        if (player.tickCount % SEND_EVERY != 0 || !DetectorMapItem.isHeld(player)) return;
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        EventState.Memory memory = Events.memory(player);
        BlockPos exact = flagged(memory, EXACT);
        BlockPos approx = flagged(memory, APPROX);
        if (exact == null && level.getGameTime() >= memory.cooldownUntil(RETRY)) {
            approx = locate(level, memory, approx);
            exact = flagged(memory, EXACT);
        }
        BlockPos shown = exact != null ? exact : approx;
        EventNet.detector(player, shown == null ? List.of() : List.of(shown));
    }

    private static BlockPos locate(ServerLevel level, EventState.Memory memory, BlockPos known) {
        BlockPos stronghold = known;
        if (stronghold == null) {
            if (!searched) {
                spawnStronghold = level.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, level.getSharedSpawnPos(),
                        SEARCH_CHUNKS, false);
                searched = true;
            }
            stronghold = spawnStronghold;
            if (stronghold == null) {
                memory.setCooldown(RETRY, level.getGameTime() + NOT_FOUND_TICKS);
                return null;
            }
            memory.setFlag(APPROX + key(stronghold));
        }
        memory.setCooldown(RETRY, level.getGameTime() + RESCAN_TICKS);
        if (level.isLoaded(stronghold)) {
            for (BlockPos pos : BlockPos.betweenClosed(stronghold.offset(-ROOM, -ROOM, -ROOM), stronghold.offset(ROOM, ROOM, ROOM))) {
                if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.END_PORTAL)) continue;
                BlockPos portal = pos.immutable();
                memory.clearFlag(APPROX + key(stronghold));
                memory.setFlag(EXACT + key(portal));
                return stronghold;
            }
        }
        return stronghold;
    }

    private static String key(BlockPos pos) { return pos.getX() + "," + pos.getY() + "," + pos.getZ(); }

    private static BlockPos flagged(EventState.Memory memory, String prefix) {
        for (String flag : memory.flags()) {
            if (!flag.startsWith(prefix)) continue;
            String[] xyz = flag.substring(prefix.length()).split(",");
            try {
                return new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
            } catch (RuntimeException e) {
                LOG.error("bad detector flag {}", flag);
            }
        }
        return null;
    }
}
