// this file builds the nether reactor room and hall in old_nether, points you at it when you
// arrive and sends you out through the exit once the core is beaten.

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.block.ModBlocks;
import com.xservernull.archivist.block.ReactorCoreBlock;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.List;

public final class OldNetherReactor {
    private OldNetherReactor() {}

    public static final String PILGRIM = "archivist_reactor_pilgrim";

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceKey<Level> OLD_NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "old_nether"));
    private static final List<String> EXITS = List.of("library", "xor_world", "m4kb_world", "mansion_dim", "echo_chamber",
            "far_lands", "bedrock_far_lands", "villllage", "neverforgetyou", "childhoodhome");

    private static final String CORE_KEY = "old_nether_core", BEAM_KEY = "old_nether_beam";
    private static final long SALT = 0x6E657468L;
    private static final int DISTANCE = 32, RADIUS = 10, HEIGHT = 12, POST = 7, HALL = 1, LAND_Y = 64;
    private static final int BEAM_TICKS = 20 * 30, BEAM_HEIGHT = 40;
    private static final int[][] CORNERS = {{POST, POST}, {-POST, POST}, {-POST, -POST}, {POST, -POST}};
    private static final int[][] SPOTS = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (!e.getTo().equals(OLD_NETHER) || !(e.getEntity() instanceof ServerPlayer player)) return;
            MinecraftServer server = player.getServer();
            if (server != null) server.execute(() -> arrive(player));
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            // Logging in inside old_nether skips the dimension-change event: set the reactor up anyway.
            if (!(e.getEntity() instanceof ServerPlayer player) || !player.level().dimension().equals(OLD_NETHER)) return;
            MinecraftServer server = player.getServer();
            if (server != null) server.execute(() -> arrive(player));
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
    }

    static void enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.getLevel(OLD_NETHER);
            if (level == null) {
                LOG.warn("[old_nether] dimension {} not found", OLD_NETHER.location());
                return;
            }
            if (!player.isAlive() || player.isSpectator()) return;
            build(level);
            BlockPos start = landing(level);
            Warp.place(player, level, start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
            if (player.level().dimension().equals(OLD_NETHER)) arrive(player); // also when already there (no dimension event)
        });
    }

    static BlockPos beacon(ServerPlayer player) {
        if (!player.level().dimension().equals(OLD_NETHER)) return null;
        ServerLevel level = player.serverLevel();
        BlockPos core = build(level);
        if (isDone(level, core)) return null;
        EventState.Memory memory = Events.memory(player);
        memory.setCooldown(CORE_KEY, core.asLong());
        memory.setCooldown(BEAM_KEY, level.getGameTime() + BEAM_TICKS);
        return core;
    }

    static BlockPos knownCore(ServerPlayer player) {
        long packed = Events.memory(player).cooldownUntil(CORE_KEY);
        return packed == 0L ? null : BlockPos.of(packed);
    }

    static boolean coreDone(ServerLevel level, BlockPos core) {
        return isDone(level, core);
    }

    private static void arrive(ServerPlayer player) {
        if (!player.isAlive() || !player.level().dimension().equals(OLD_NETHER)) return;
        ServerLevel level = player.serverLevel();
        BlockPos core = build(level);
        boolean done = isDone(level, core);
        EventState.Memory memory = Events.memory(player);
        memory.setCooldown(CORE_KEY, core.asLong());
        memory.setCooldown(BEAM_KEY, done ? 0 : level.getGameTime() + BEAM_TICKS);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(done ? exitOf(core) : core));
        LOG.info("[old_nether] {} arrived, reactor core at {}", player.getName().getString(), core.toShortString());
    }

    private static void tick(ServerPlayer player) {
        if (player.tickCount % 5 != 0 || !player.isAlive() || player.isSpectator()) return;
        if (!player.level().dimension().equals(OLD_NETHER)) return;
        ServerLevel level = player.serverLevel();
        if (level.getBlockState(player.blockPosition()).is(ModBlocks.REACTOR_EXIT.get())) {
            leave(player, level);
        } else if (player.tickCount % 10 == 0) {
            beam(player, level);
        }
    }

    private static void beam(ServerPlayer player, ServerLevel level) {
        EventState.Memory memory = Events.memory(player);
        if (level.getGameTime() >= memory.cooldownUntil(BEAM_KEY)) return;
        BlockPos core = BlockPos.of(memory.cooldownUntil(CORE_KEY));
        if (Ground.isLoaded(level, core.getX(), core.getZ()) && isDone(level, core)) return;
        level.sendParticles(player, ParticleTypes.SOUL_FIRE_FLAME, true, core.getX() + 0.5, core.getY() + BEAM_HEIGHT / 2.0, core.getZ() + 0.5,
                12, 0.15, BEAM_HEIGHT / 2.0, 0.15, 0.0);
    }

    private static void leave(ServerPlayer player, ServerLevel level) {
        EventState.Memory memory = Events.memory(player);
        List<String> fresh = EXITS.stream().filter(id -> !memory.hasFlag(DimensionProgress.VISIT + id)).toList();
        List<String> pool = fresh.isEmpty() ? EXITS : fresh;
        String to = pool.get(player.getRandom().nextInt(pool.size()));
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.3, 0.5, 0.3, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5F, 0.8F);
        LOG.info("[old_nether] {} took the reactor exit to {}", player.getName().getString(), to);
        MinecraftServer server = player.getServer();
        if (server != null) server.execute(() -> AdminPanelNet.warp(player, to));
    }

    private static boolean isDone(ServerLevel level, BlockPos core) {
        BlockState state = level.getBlockState(core);
        return !state.is(ModBlocks.REACTOR_CORE.get()) || state.getValue(ReactorCoreBlock.DONE);
    }

    private static BlockPos exitOf(BlockPos core) {
        return core.offset(0, -1, 3);
    }

    public static void openExit(ServerLevel level, BlockPos core) {
        BlockPos low = exitOf(core);
        BlockState exit = ModBlocks.REACTOR_EXIT.get().defaultBlockState();
        level.setBlock(low, exit, Block.UPDATE_ALL);
        level.setBlock(low.above(), exit, Block.UPDATE_ALL);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, low.getX() + 0.5, low.getY() + 1.0, low.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.05);
    }

    public static void lights(ServerLevel level, BlockPos core, int lit) {
        for (int i = 0; i < CORNERS.length; i++) {
            BlockPos top = core.offset(CORNERS[i][0], 2, CORNERS[i][1]);
            BlockState now = level.getBlockState(top);
            if (!now.is(Blocks.GLOWSTONE) && !now.is(Blocks.BLACKSTONE)) continue;
            BlockState next = (i < lit ? Blocks.GLOWSTONE : Blocks.BLACKSTONE).defaultBlockState();
            if (now != next) level.setBlock(top, next, Block.UPDATE_ALL);
        }
    }

    private static BlockPos landing(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        BlockState rack = ModBlocks.OLD_NETHERRACK.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos floor = new BlockPos(spawn.getX() + dx, LAND_Y - 1, spawn.getZ() + dz);
                if (!level.getBlockState(floor).is(rack.getBlock())) put(level, floor, rack);
                for (int y = LAND_Y; y < LAND_Y + 4; y++) {
                    BlockPos pos = new BlockPos(spawn.getX() + dx, y, spawn.getZ() + dz);
                    if (!level.getBlockState(pos).isAir()) put(level, pos, air);
                }
            }
        }
        return new BlockPos(spawn.getX(), LAND_Y, spawn.getZ());
    }

    private static BlockPos build(ServerLevel level) {
        BlockPos start = landing(level);
        double angle = RandomSource.create(level.getSeed() ^ SALT).nextDouble() * Math.PI * 2.0;
        BlockPos core = new BlockPos(start.getX() + (int) Math.round(Math.cos(angle) * DISTANCE),
                start.getY() + 1, start.getZ() + (int) Math.round(Math.sin(angle) * DISTANCE));
        level.getChunk(core.getX() >> 4, core.getZ() >> 4);
        if (level.getBlockState(core).is(ModBlocks.REACTOR_CORE.get()) || level.getBlockState(exitOf(core)).is(ModBlocks.REACTOR_EXIT.get())) {
            return core;
        }
        room(level, core);
        hall(level, start, core);
        pilgrims(level, core);
        LOG.info("[old_nether] built the reactor at {}, hall starts at {}", core.toShortString(), start.toShortString());
        return core;
    }

    private static void room(ServerLevel level, BlockPos core) {
        int feet = core.getY() - 1;
        BlockState rack = ModBlocks.OLD_NETHERRACK.get().defaultBlockState();
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > (RADIUS + 1) * (RADIUS + 1)) continue;
                int x = core.getX() + dx, z = core.getZ() + dz;
                boolean inside = d2 <= RADIUS * RADIUS;
                boolean ring = d2 >= 31 && d2 <= 42;
                put(level, new BlockPos(x, feet - 2, z), rack);
                put(level, new BlockPos(x, feet - 1, z), ring ? gold : rack);
                for (int y = feet; y < feet + HEIGHT; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (inside) {
                        if (!level.getBlockState(pos).isAir()) put(level, pos, Blocks.AIR.defaultBlockState());
                    } else {
                        BlockState old = level.getBlockState(pos);
                        if (old.isAir() || !old.getFluidState().isEmpty()) put(level, pos, rack);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) put(level, core.offset(dx, -1, dz), gold);
        }
        for (int[] corner : CORNERS) {
            for (int y = 0; y < 3; y++) put(level, core.offset(corner[0], y - 1, corner[1]), rack);
            put(level, core.offset(corner[0], 2, corner[1]), Blocks.GLOWSTONE.defaultBlockState());
        }
        level.setBlock(core, ModBlocks.REACTOR_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void hall(ServerLevel level, BlockPos start, BlockPos core) {
        int dx = core.getX() - start.getX(), dz = core.getZ() - start.getZ();
        int length = Math.max(Math.abs(dx), Math.abs(dz));
        int feet = start.getY();
        BlockState rack = ModBlocks.OLD_NETHERRACK.get().defaultBlockState();
        for (int i = 0; i <= length; i++) {
            int x = start.getX() + Math.round(dx * (float) i / length);
            int z = start.getZ() + Math.round(dz * (float) i / length);
            for (int ox = -HALL; ox <= HALL; ox++) {
                for (int oz = -HALL; oz <= HALL; oz++) {
                    // Never carve inside the room: this used to wipe the core, its gold pad and the posts (y 64 to 67 at the core's column).
                    if (insideRoom(core, x + ox, z + oz)) continue;
                    BlockPos floor = new BlockPos(x + ox, feet - 1, z + oz);
                    if (!level.getBlockState(floor).isSolid()) put(level, floor, rack);
                    for (int y = feet; y < feet + 4; y++) {
                        BlockPos pos = new BlockPos(x + ox, y, z + oz);
                        if (!level.getBlockState(pos).isAir()) put(level, pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            if (i % 8 == 4) {
                for (int side : new int[] {-HALL - 1, HALL + 1}) {
                    BlockPos lamp = new BlockPos(x + side, feet + 2, z);
                    if (!insideRoom(core, lamp.getX(), lamp.getZ()) && level.getBlockState(lamp).isSolid()) put(level, lamp, Blocks.GLOWSTONE.defaultBlockState());
                }
            }
        }
    }

    private static boolean insideRoom(BlockPos core, int x, int z) {
        int dx = x - core.getX(), dz = z - core.getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    private static void pilgrims(ServerLevel level, BlockPos core) {
        for (int[] spot : SPOTS) {
            ZombifiedPiglin pilgrim = EntityType.ZOMBIFIED_PIGLIN.create(level);
            if (pilgrim == null) continue;
            float yaw = (float) (Mth.atan2(-spot[1], -spot[0]) * 180.0 / Math.PI) - 90.0F;
            pilgrim.moveTo(core.getX() + spot[0] + 0.5, core.getY() - 1, core.getZ() + spot[1] + 0.5, yaw, 0.0F);
            pilgrim.setYHeadRot(yaw);
            pilgrim.setYBodyRot(yaw);
            pilgrim.finalizeSpawn(level, level.getCurrentDifficultyAt(pilgrim.blockPosition()), MobSpawnType.EVENT, null);
            pilgrim.setNoAi(true);
            pilgrim.setInvulnerable(true);
            pilgrim.setSilent(true);
            pilgrim.setPersistenceRequired();
            pilgrim.addTag(PILGRIM);
            level.addFreshEntity(pilgrim);
        }
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.isOutsideBuildHeight(pos) || level.getBlockState(pos).is(Blocks.BEDROCK)) return;
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}