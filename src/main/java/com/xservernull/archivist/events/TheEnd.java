// the archivist rises in the end (old theendevent).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.ArchivistDragon;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class TheEnd {
    private TheEnd() {}

    private static final Logger LOG = LogUtils.getLogger();

    public static final String TALK = "the_end_talk";

    static final String PENDING = "the_end_pending";
    static final String RISEN = "the_end_risen";
    private static final String RISE_TIMER = "the_end_rise";
    private static final String ENDING_PREFIX = "the_end_ending_";
    private static final String PLACED = "the_end_ending_placed";

    private static final int RISE_DELAY = 80;
    private static final int CLEANUP_EVERY = 20;
    private static final double X = 0.5, Y = 72.0, Z = 0.5;
    private static final double WATCH_DISTANCE = 20.0;
    private static final int ENDING_AHEAD = 24;
    private static final int ENDING_Y = 60;

    public enum Ending {
        POSITIVE, NEUTRAL, NEGATIVE;

        public String flag() { return ENDING_PREFIX + name().toLowerCase(Locale.ROOT); }
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener(TheEnd::onDragonDeath);
        NeoForge.EVENT_BUS.addListener(TheEnd::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(TheEnd::onTravel);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> cleanup(e.getServer()));
    }

    // ---------------------------------------------------------------- queries (bed handler, talk)

    public static boolean hasTriggered(ServerPlayer player) {
        return Events.memory(player).hasFlag(RISEN);
    }

    public static boolean hasEnding(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        for (Ending ending : Ending.values()) {
            if (memory.hasFlag(ending.flag())) return true;
        }
        return false;
    }

    public static void finish(ServerPlayer player, Ending ending) {
        Events.memory(player).setFlag(ending.flag());
        LOG.info("[the_end] {} reached the {} ending", player.getName().getString(), ending);
        place(player, ending);
    }

    public static void interrupted(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        memory.clearFlag(RISEN);
        memory.setFlag(PENDING);
        memory.setCooldown(RISE_TIMER, 0L);
    }

    // ---------------------------------------------------------------- the ending structure

    private static BlockPos arrival(Ending ending) {
        return switch (ending) {
            case POSITIVE -> new BlockPos(2, 2, 9); // the carpet inside the room; (1, 2, 9) is the closed door
            case NEUTRAL -> new BlockPos(2, 1, 2);
            case NEGATIVE -> new BlockPos(101, 2, 3);
        };
    }

    private static String template(Ending ending) {
        return switch (ending) {
            case POSITIVE -> "goodending";
            case NEUTRAL -> "neutralending";
            case NEGATIVE -> "badending";
        };
    }

    private static void place(ServerPlayer player, Ending ending) {
        EventState.Memory memory = Events.memory(player);
        ServerLevel level = player.serverLevel();
        if (memory.hasFlag(PLACED) || level.dimension() != Level.END) return;
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, template(ending));
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty()) {
            LOG.warn("[the_end] structure {} not found (data/{}/structure/{}.nbt), nothing placed",
                    id, id.getNamespace(), id.getPath());
            return;
        }
        StructureTemplate structure = found.get();
        BlockPos origin = new BlockPos(player.getBlockX(), ENDING_Y, player.getBlockZ() + ENDING_AHEAD);
        int x1 = origin.getX() + structure.getSize().getX() - 1;
        int z1 = origin.getZ() + structure.getSize().getZ() - 1;
        for (int cx = origin.getX() >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= z1 >> 4; cz++) level.getChunk(cx, cz);
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!structure.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            LOG.warn("[the_end] {} was not placed at {}", id, origin);
            return;
        }
        memory.setFlag(PLACED);
        BlockPos at = origin.offset(arrival(ending));
        LOG.info("[the_end] placed {} at {}", id, origin);
        Warp.place(player, level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- the rise

    private static void onDragonDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon) || !(dragon.level() instanceof ServerLevel level)) return;
        if (level.dimension() != Level.END) return;
        long due = level.getGameTime() + RISE_DELAY;
        for (ServerPlayer player : level.players()) {
            EventState.Memory memory = Events.memory(player);
            if (memory.hasFlag(RISEN)) continue;
            memory.setFlag(PENDING);
            memory.setCooldown(RISE_TIMER, due);
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
        if (player.level().dimension() != Level.END) return;
        EventState.Memory memory = Events.memory(player);
        if (!memory.hasFlag(PENDING) || player.level().getGameTime() < memory.cooldownUntil(RISE_TIMER)) return;
        rise(player, memory);
    }

    private static void onTravel(EntityTravelToDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != Level.END || event.getDimension() != Level.OVERWORLD) return;
        if (Events.memory(player).hasFlag(PENDING)) event.setCanceled(true);
    }

    private static void rise(ServerPlayer player, EventState.Memory memory) {
        ServerLevel level = player.serverLevel();
        memory.clearFlag(PENDING);
        memory.setFlag(RISEN);
        if (dragons(level).isEmpty()) {
            ArchivistDragon dragon = new ArchivistDragon(Mobs.ARCHIVIST_DRAGON.get(), level);
            dragon.setPos(X, Y, Z);
            level.addFreshEntity(dragon);
            LOG.info("[the_end] the Archivist has risen at y={}", Y);
        }
        double z = Z + WATCH_DISTANCE;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(X), (int) Math.floor(z));
        player.teleportTo(level, X, y, z, 180.0F, 0.0F);
        if (!Events.start(player, TALK)) {
            LOG.warn("[the_end] no event '{}': {} sees the Archivist and nothing else", TALK, player.getName().getString());
        }
    }

    // ---------------------------------------------------------------- the dragon goes

    private static void cleanup(MinecraftServer server) {
        if (server.getTickCount() % CLEANUP_EVERY != 0) return;
        ServerLevel end = server.getLevel(Level.END);
        if (end == null) return;
        List<? extends ArchivistDragon> dragons = dragons(end);
        if (dragons.isEmpty()) return;
        for (ServerPlayer player : end.players()) {
            if (TALK.equals(Events.running(player)) || Events.memory(player).hasFlag(PENDING)) return;
        }
        dragons.forEach(ArchivistDragon::explodeAndDiscard);
    }

    private static List<? extends ArchivistDragon> dragons(ServerLevel level) {
        return level.getEntities(Mobs.ARCHIVIST_DRAGON.get(), dragon -> true);
    }
}