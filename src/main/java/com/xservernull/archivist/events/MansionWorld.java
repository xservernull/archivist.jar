// how you get into, around and out of archivist:mansion_dim (old portaltriggers,
// respawnrouter, worldeventhub, teleporthandler): in: falling out of the world in the library

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.OptionalInt;

final class MansionWorld {
    private MansionWorld() {}

    private static final Logger LOG = LogUtils.getLogger();

    static final ResourceKey<Level> MANSION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "mansion_dim"));

    static final int FIRST_Y = 65, LAST_Y = 112;
    private static final int STAIRS_Y = 70, STAIRS_TICKS = 60;
    private static final String STAIRS_KEY = "mansion_stairs";
    private static final String DIED_FLAG = "mansion_died";
    private static final int ECHO_FROM_Y = 40;

    static void listen() {
        NeoForge.EVENT_BUS.addListener(MansionWorld::onVoidFall);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MansionWorld::onDeathInMansion);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, MansionWorld::onChangedDimension);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, MansionWorld::onRespawn);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> start(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    // ---------------------------------------------------------------- in

    private static void onVoidFall(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != Archivist.LIBRARY) return;
        if (!e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        e.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        server.execute(() -> toMansion(player));
    }

    private static void onDeathInMansion(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer player && player.level().dimension() == MANSION) {
            Events.memory(player).setFlag(DIED_FLAG);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        EventState.Memory memory = Events.memory(player);
        if (!memory.hasFlag(DIED_FLAG)) return;
        memory.clearFlag(DIED_FLAG);
        toMansion(player);
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        start(e.getEntity());
    }

    private static void start(Player entity) {
        if (entity instanceof ServerPlayer player && player.level().dimension().equals(MANSION)) {
            Events.start(player, MansionHunt.ID);
        }
    }

    static void toMansion(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(MANSION);
        if (level == null) {
            LOG.warn("[mansion] dimension {} not found", MANSION.location());
            return;
        }
        BlockPos at = spawnSpot(level);
        Warp.place(player, level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        LOG.info("[mansion] {} sent to the mansion at {}", player.getGameProfile().getName(), at);
    }

    static BlockPos spawnSpot(ServerLevel level) {
        BlockPos origin = level.getSharedSpawnPos();
        for (int r = 0; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = origin.getX() + dx, z = origin.getZ() + dz;
                    level.getChunk(x >> 4, z >> 4);
                    for (int y = FIRST_Y; y <= LAST_Y; y++) {
                        BlockPos feet = new BlockPos(x, y, z);
                        if (standable(level, feet)) return feet;
                    }
                }
            }
        }
        BlockPos feet = new BlockPos(origin.getX(), FIRST_Y, origin.getZ());
        level.removeBlock(feet, false);
        level.removeBlock(feet.above(), false);
        LOG.warn("[mansion] no free floor near the world spawn, carved one at {}", feet);
        return feet;
    }

    static boolean standable(ServerLevel level, BlockPos feet) {
        return level.getBlockState(feet).isAir()
                && level.getBlockState(feet.above()).isAir()
                && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP);
    }

    // ---------------------------------------------------------------- around and out

    private static void tick(ServerPlayer player) {
        if (!player.level().dimension().equals(MANSION)) return;
        stairs(player);
    }

    private static void stairs(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        long until = memory.cooldownUntil(STAIRS_KEY);
        if (player.getBlockY() < STAIRS_Y) {
            if (until != 0) memory.setCooldown(STAIRS_KEY, 0);
            return;
        }
        long now = player.level().getGameTime();
        if (until == 0) {
            memory.setCooldown(STAIRS_KEY, now + STAIRS_TICKS);
            player.sendSystemMessage(Texts.tr("archivist.msg.mansion_pull", "\u00a77Something is pulling you upstairs\u2026"));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.AMBIENT, 0.6F, 0.8F);
        } else if (now >= until) {
            memory.setCooldown(STAIRS_KEY, 0);
            toEcho(player);
        }
    }

    private static void toEcho(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(Archivist.ECHO_CHAMBER);
        if (level == null) {
            LOG.warn("[mansion] dimension {} not found", Archivist.ECHO_CHAMBER.location());
            return;
        }
        BlockPos origin = level.getSharedSpawnPos();
        for (int r = 0; r <= 6; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = origin.getX() + dx, z = origin.getZ() + dz;
                    level.getChunk(x >> 4, z >> 4);
                    OptionalInt y = Ground.standY(level, x, ECHO_FROM_Y, z);
                    if (y.isPresent()) {
                        Warp.place(player, level, x + 0.5, y.getAsInt(), z + 0.5);
                        LOG.info("[mansion] {} was pulled upstairs into the echo chamber", player.getGameProfile().getName());
                        return;
                    }
                }
            }
        }
        LOG.warn("[mansion] no floor found in the echo chamber near {}", origin);
    }
}