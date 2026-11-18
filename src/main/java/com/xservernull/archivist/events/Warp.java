// the teleports the quietplace and childhoodhome stories share (old teleporthandler, only the
// parts these two need): into the quiet place, into the childhood home, and back to the

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

public final class Warp {
    private Warp() {}

    private static final Logger LOG = LogUtils.getLogger();

    static boolean toQuietPlace(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(Archivist.QUIET_PLACE);
        if (level == null) {
            LOG.warn("[warp] dimension {} not found", Archivist.QUIET_PLACE.location());
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        double y = Ground.openY(level, spawn.getX(), spawn.getZ(), spawn.getY());
        return place(player, level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
    }

    static boolean toChildhoodHome(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(ChildhoodHome.CHILDHOOD_HOME);
        if (level == null) {
            LOG.warn("[warp] dimension {} not found", ChildhoodHome.CHILDHOOD_HOME.location());
            return false;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        double y = Ground.openY(level, spawn.getX(), spawn.getZ(), spawn.getY());
        return place(player, level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
    }

    static boolean toOverworld(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel overworld = server == null ? null : server.getLevel(Level.OVERWORLD);
        if (overworld == null) return false;
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && Level.OVERWORLD.equals(player.getRespawnDimension())) {
            return place(player, overworld, respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5);
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        double y = Ground.openY(overworld, spawn.getX(), spawn.getZ(), spawn.getY());
        return place(player, overworld, spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
    }

    public static boolean place(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
        BlockPos safe = Ground.safeSpot(level, bx, by, bz).orElse(null);
        if (safe == null) {
            safe = Ground.makePad(level, bx, by, bz);
            LOG.warn("[warp] no safe spot near {} {} {} in {}, made a stone pad", bx, by, bz, level.dimension().location());
        }
        if (safe.getX() != bx || safe.getZ() != bz || safe.getY() != by) {
            x = safe.getX() + (safe.getX() == bx ? x - bx : 0.5);
            z = safe.getZ() + (safe.getZ() == bz ? z - bz : 0.5);
            y = safe.getY();
        }
        player.fallDistance = 0;
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        boolean moved = player.level() == level;
        LOG.info("[warp] {} -> {} at {} {} {}", player.getGameProfile().getName(), level.dimension().location(),
                (int) x, (int) y, (int) z);
        return moved;
    }
}