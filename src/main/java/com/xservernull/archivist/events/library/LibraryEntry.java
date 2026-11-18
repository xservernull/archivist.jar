// sends a player into the library when they place a bed on a bookshelf square also keeps toast
// around, spawns her on arrival and lets her come back after she dies

package com.xservernull.archivist.events.library;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Toast;
import com.xservernull.archivist.events.Events;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class LibraryEntry {
    private LibraryEntry() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final double ENTRY_X = 22.5, ENTRY_Y = 61, ENTRY_Z = 9.5;

    private static final int TELEPORT_DELAY = 1, TOAST_DELAY = 20;

    private static final Map<UUID, Integer> TELEPORTS = new HashMap<>();
    private static final Map<UUID, Integer> TOASTS = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LibraryEntry::onPlace);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener(LibraryEntry::onDeath);
        NeoForge.EVENT_BUS.addListener(LibraryEntry::onArrive);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            TELEPORTS.clear();
            TOASTS.clear();
        });
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!(e.getPlacedBlock().getBlock() instanceof BedBlock)) return;
        if (player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        if (!onBookshelfSquare(e.getLevel(), e.getPos())) return;
        TELEPORTS.putIfAbsent(player.getUUID(), TELEPORT_DELAY);
    }

    private static boolean onBookshelfSquare(LevelAccessor level, BlockPos bed) {
        BlockPos below = bed.below();
        if (!level.getBlockState(below).is(Blocks.BOOKSHELF)) return false;
        for (int dx = -2; dx <= 0; dx++) {
            for (int dz = -2; dz <= 0; dz++) {
                if (isSquare(level, below.offset(dx, 0, dz))) return true;
            }
        }
        return false;
    }

    private static boolean isSquare(LevelAccessor level, BlockPos corner) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                if (!level.getBlockState(corner.offset(x, 0, z)).is(Blocks.BOOKSHELF)) return false;
            }
        }
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (TELEPORTS.isEmpty() && TOASTS.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Integer>> it = TELEPORTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            entry.setValue(left);
            if (left > 0) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel library = server.getLevel(Archivist.LIBRARY);
            if (player == null || !player.isAlive()) continue;
            if (library == null) {
                LOG.error("library dimension missing");
                continue;
            }
            TOASTS.put(player.getUUID(), TOAST_DELAY);
            com.xservernull.archivist.events.Warp.place(player, library, ENTRY_X, ENTRY_Y, ENTRY_Z);
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = TOASTS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            entry.setValue(left);
            if (left > 0) continue;
            it.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && player.level().dimension() == Archivist.LIBRARY) {
                spawnToastIfNeeded(player.serverLevel(), player);
                if (!Events.memory(player).hasSeen("toast_intro")) Events.start(player, "toast_intro");
            }
        }
    }

    private static void onArrive(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !e.getTo().equals(Archivist.LIBRARY)) return;
        if (TOASTS.containsKey(player.getUUID())) return;
        spawnToastIfNeeded(player.serverLevel(), player);
    }

    private static final class Data extends SavedData {
        private static final String NAME = "archivist_library";
        boolean toast;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.toast = tag.getBoolean("toast");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("toast", toast);
            return tag;
        }
    }

    private static void spawnToastIfNeeded(ServerLevel level, ServerPlayer player) {
        Data data = Data.get(level.getServer());
        if (data.toast) {
            if (!level.getEntities(EntityTypeTest.forClass(Toast.class), t -> t.isAlive()).isEmpty()) return;
        }
        BlockPos spot = spotNear(level, player);
        Toast toast = Toast.TYPE.get().create(level);
        if (toast == null) return;
        toast.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot() + 180f, 0f);
        toast.setPersistenceRequired();
        level.addFreshEntity(toast);
        data.toast = true;
        data.setDirty();
    }

    private static BlockPos spotNear(ServerLevel level, ServerPlayer player) {
        BlockPos at = player.blockPosition();
        for (int step = 0; step < 8; step++) {
            double angle = Math.toRadians(step * 45);
            int dx = (int) Math.round(Math.sin(angle) * 2);
            int dz = (int) Math.round(Math.cos(angle) * 2);
            BlockPos feet = at.offset(dx, 0, dz);
            boolean room = level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir();
            boolean floor = level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
            if (room && floor && level.getEntities(null, new net.minecraft.world.phys.AABB(feet).deflate(0.1)).isEmpty()) {
                return feet;
            }
        }
        return at;
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Toast toast) || toast.level().isClientSide) return;
        if (toast.level().dimension() != Archivist.LIBRARY) return;
        Data data = Data.get(toast.getServer());
        data.toast = false;
        data.setDirty();
    }
}