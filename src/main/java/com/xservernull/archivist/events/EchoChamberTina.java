// echo_chamber: tina. it runs for every player who is in the dimension, each with their own
// tina (host or not).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Tina;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

final class EchoChamberTina implements QuickEvent {
    static final String ID = "echo_chamber_tina";

    private static final Logger LOG = LogUtils.getLogger();

    private static final int ACTIVATE_AT = 8 * 20; // was 20 s; she wakes sooner
    private static final int TRY_EVERY = 10;
    private static final int GIVE_UP_AT = 100;
    private static final int REFILL_EVERY = 60;
    private static final double MIN_RADIUS = 20.0;
    private static final double MAX_RADIUS = 45.0;
    private static final int MAX_HEIGHT_OFFSET = 12;
    private static final int SPOT_ATTEMPTS = 16;
    private static final int FUNNY_EVERY = 20 * 120;
    private static final float FUNNY_VOLUME = 0.33F;
    private static final int WIN_Y = 300;
    private static final int LOSE_Y = -55;
    private static final String FELL_FLAG = "echo_chamber_fell";
    private static final String MET_FLAG = "echo_chamber_met";
    private static final double MEET_DIST = 3.0;
    private static final double FORCED_DIST = 7.0;

    @Override public String id() { return ID; }

    @Override
    public void start(ServerPlayer player) {
        discardOwned(player); // a Tina left from an earlier run or a reload
        Events.memory(player).clearFlag(FELL_FLAG);
        Events.memory(player).clearFlag(MET_FLAG);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        if (age % FUNNY_EVERY == 1) {
            level.playSound(null, player.blockPosition(), Sounds.FUNNY_AUDIO.get(), SoundSource.AMBIENT, FUNNY_VOLUME, 1.0F);
        }
        int want = ArchivistConfig.echoTinas();
        if (age < ACTIVATE_AT) {
            if (age <= GIVE_UP_AT && age % TRY_EVERY == 0 && owned(level, player).size() < want) {
                spawn(level, player, age == GIVE_UP_AT, want);
            }
        } else if (age == ACTIVATE_AT) {
            List<Tina> tinas = owned(level, player);
            for (Tina tina : tinas) tina.startChase();
            if (!tinas.isEmpty()) {
                LOG.info("[events] echo_chamber: {} Tina(s) chasing {}", tinas.size(), player.getName().getString());
            }
        } else if (age % REFILL_EVERY == 0 && owned(level, player).size() < want) {
            // old entityspawner refill: a killed Tina is replaced, and the new one chases at once
            spawn(level, player, true, want);
            for (Tina tina : owned(level, player)) {
                if (tina.phase() == Tina.Phase.STILL) tina.startChase();
            }
            LOG.info("[events] echo_chamber: Tina refilled for {}", player.getName().getString());
        }
        if (!Events.memory(player).hasFlag(MET_FLAG)) {
            for (Tina tina : owned(level, player)) {
                if (tina.phase() == Tina.Phase.CHASE && tina.distanceTo(player) <= MEET_DIST) {
                    Events.memory(player).setFlag(MET_FLAG);
                    LOG.info("[events] echo_chamber: {} met Tina", player.getName().getString());
                    break;
                }
            }
        }
        if (level.dimension().equals(Archivist.ECHO_CHAMBER)) checkHeight(player); // last: it can change the player's dimension
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        discardOwned(player);
    }

    // ---- the way out and the way down ----

    private static void checkHeight(ServerPlayer player) {
        int y = player.getBlockY();
        EventState.Memory memory = Events.memory(player);
        if (y >= WIN_Y) {
            if (memory.hasFlag(MET_FLAG)) escape(player);
            else forceMeeting(player);
        } else if (y <= LOSE_Y) {
            if (!memory.hasFlag(FELL_FLAG)) {
                memory.setFlag(FELL_FLAG);
                player.sendSystemMessage(Texts.tr("archivist.msg.echo_fell", "\u00a77It was upstairs\u2026 what a shame."));
                LOG.info("[events] {} fell downstairs in echo_chamber", player.getName().getString());
            }
        } else {
            memory.clearFlag(FELL_FLAG);
        }
    }

    private static void forceMeeting(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (Tina tina : owned(level, player)) {
            if (tina.distanceTo(player) <= FORCED_DIST * 2.0) {
                if (tina.phase() == Tina.Phase.STILL) tina.startChase();
                return;
            }
        }
        double angle = player.getRandom().nextDouble() * Math.PI * 2;
        double x = player.getX() + Math.cos(angle) * FORCED_DIST;
        double z = player.getZ() + Math.sin(angle) * FORCED_DIST;
        Tina tina = Mobs.TINA.get().create(level);
        if (tina == null) return;
        tina.moveTo(x, player.getY(), z, player.getRandom().nextFloat() * 360.0F, 0.0F);
        tina.setOwner(player.getUUID());
        level.addFreshEntity(tina);
        tina.startChase();
        LOG.info("[events] echo_chamber: {} reached the top without meeting Tina, one is sent", player.getName().getString());
    }

    private static void escape(ServerPlayer player) {
        if (player.getServer() == null) return;
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        BlockPos at = player.getRespawnPosition() != null && Level.OVERWORLD.equals(player.getRespawnDimension())
                ? player.getRespawnPosition()
                : overworld.getSharedSpawnPos();
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        Warp.place(player, overworld, at.getX() + 0.5, y, at.getZ() + 0.5);
        LOG.info("[events] {} escaped echo_chamber upstairs", player.getName().getString());
    }

    // ---- spawning ----

    private static void spawn(ServerLevel level, ServerPlayer player, boolean force, int want) {
        for (int i = owned(level, player).size(); i < want; i++) {
            Vec3 at = pickSpot(level, player);
            if (at == null) {
                if (!force) return;
                at = player.position().add(8.0 + i, 0.0, 0.0); // last resort: right beside the player, whose chunk is loaded
            }
            Tina tina = Mobs.TINA.get().create(level);
            if (tina == null) return;
            tina.moveTo(at.x, at.y, at.z, player.getRandom().nextFloat() * 360.0F, 0.0F);
            tina.setOwner(player.getUUID());
            level.addFreshEntity(tina);
            LOG.info("[events] echo_chamber: Tina placed {} blocks from {}", (int) at.distanceTo(player.position()), player.getName().getString());
        }
    }

    private static Vec3 pickSpot(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double radius = MIN_RADIUS + player.getRandom().nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
            if (!Ground.isLoaded(level, x, z)) continue;
            int fromY = player.getBlockY() + player.getRandom().nextInt(MAX_HEIGHT_OFFSET * 2 + 1) - MAX_HEIGHT_OFFSET;
            fromY = Mth.clamp(fromY, level.getMinBuildHeight() + 4, level.getMaxBuildHeight() - 4);
            OptionalInt y = Ground.standY(level, x, fromY, z);
            if (y.isPresent()) return new Vec3(x + 0.5, y.getAsInt(), z + 0.5);
        }
        return null;
    }

    // ---- finding her ----

    private static List<Tina> owned(ServerLevel level, ServerPlayer player) {
        List<Tina> out = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e instanceof Tina tina && tina.isOwnedBy(player.getUUID())) out.add(tina);
        }
        return out;
    }

    private static void discardOwned(ServerPlayer player) {
        List<ServerLevel> levels = new ArrayList<>();
        levels.add(player.serverLevel());
        if (player.getServer() != null) {
            ServerLevel echo = player.getServer().getLevel(Archivist.ECHO_CHAMBER);
            if (echo != null && !levels.contains(echo)) levels.add(echo);
        }
        List<Entity> found = new ArrayList<>();
        for (ServerLevel level : levels) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Tina tina && tina.isOwnedBy(player.getUUID())) found.add(e);
            }
        }
        found.forEach(Entity::discard);
    }

    // ---- when it starts ----

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, (PlayerEvent.PlayerChangedDimensionEvent e) -> enter(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, (PlayerEvent.PlayerRespawnEvent e) -> enter(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> enter(e.getEntity()));
    }

    private static void enter(Player entity) {
        if (entity instanceof ServerPlayer player && player.level().dimension().equals(Archivist.ECHO_CHAMBER)) {
            Events.start(player, ID);
        }
    }
}