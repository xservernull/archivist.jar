// the way into archivist:infdev20100227 (old portaltriggers glitch patch, *
// dimensiontransitionpacket invert_fall and infdevfallcamerahandler).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InfdevEntry {
    private InfdevEntry() {}

    private static final Logger LOG = LogUtils.getLogger();

    static final String PATCH_ID = "glitch_patch";
    static final String FALL_ID = "infdev_fall";

    private static final ResourceKey<Level> INFDEV =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("archivist", "infdev20100227"));
    private static final ResourceKey<Level> M4KB_WORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("archivist", "m4kb_world"));

    static final int MIN_DAY = 10;
    static final int LIFETIME = 300;
    static final int SHAFT_DEPTH = 40;
    private static final int HALF_X = 3, NEG_Z = 3, POS_Z = 4;
    static final int FALL_DEPTH = 12;
    private static final int FALL_FX_TICKS = 60, ARRIVE_FX_TICKS = 60;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    static void listen() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) watch(player);
        });
    }

    // ---------------------------------------------------------------- the patch

    static final class Patch implements QuickEvent {
        @Override public String id() { return PATCH_ID; }

        @Override
        public void start(ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            if (!level.dimension().equals(Level.OVERWORLD) || level.getDayTime() / 24000L < MIN_DAY) return;
            RandomSource random = player.getRandom();
            for (int attempt = 0; attempt < 12; attempt++) {
                int dx = random.nextInt(17) - 8, dz = random.nextInt(17) - 8;
                if (Math.abs(dx) + Math.abs(dz) < 3) continue;
                BlockPos ground = findGround(level, player.blockPosition().offset(dx, 0, dz));
                if (ground == null) continue;
                Data data = Data.get(level.getServer());
                if (data.near(ground)) continue;
                Shaft shaft = carve(level, ground);
                data.shafts.add(shaft);
                data.setDirty();
                LOG.info("[infdev] glitch patch opened at {} by {} ({} blocks, heals in {} ticks)",
                        ground, player.getName().getString(), shaft.positions.size(), LIFETIME);
                return;
            }
            LOG.warn("[infdev] no ground for a glitch patch near {}", player.blockPosition());
        }

        @Override public boolean tick(ServerPlayer player, int age) { return true; }
    }

    private static final int[][] NEAR = {
            {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3},
            {5, 5}, {5, -5}, {-5, 5}, {-5, -5},
            {8, 0}, {-8, 0}, {0, 8}, {0, -8}};

    static boolean openNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD)) {
            player.displayClientMessage(Texts.tr("archivist.pit.overworld", "the pit only opens in the overworld."), true);
            return false;
        }
        Data data = Data.get(level.getServer());
        if (!data.shafts.isEmpty()) {
            player.displayClientMessage(Texts.tr("archivist.pit.settling", "the pit is still settling."), true);
            return false;
        }
        for (int[] offset : NEAR) {
            BlockPos ground = findGround(level, player.blockPosition().offset(offset[0], 0, offset[1]));
            if (ground == null) continue;
            Shaft shaft = carve(level, ground);
            data.shafts.add(shaft);
            data.setDirty();
            LOG.info("[infdev] glitch patch force-opened at {} by {} ({} blocks, heals in {} ticks)",
                    ground, player.getName().getString(), shaft.positions.size(), LIFETIME);
            return true;
        }
        LOG.warn("[infdev] no ground for the pit near {}", player.blockPosition());
        player.displayClientMessage(Texts.tr("archivist.pit.no_spot", "couldn't find a valid spot to open the pit nearby."), true);
        return false;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getFluidState(pos).isEmpty() && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static BlockPos findGround(ServerLevel level, BlockPos near) {
        int bottom = level.getMinBuildHeight() + SHAFT_DEPTH;
        int top = Math.min(near.getY() + 8, level.getMaxBuildHeight() - 3);
        for (int y = top; y >= bottom; y--) {
            BlockPos check = new BlockPos(near.getX(), y, near.getZ());
            if (!level.hasChunkAt(check)) return null;
            if (open(level, check)) continue;
            if (!level.getFluidState(check).isEmpty() || !level.getBlockState(check).isSolid()) return null; // water, a leaf roof
            return open(level, check.above()) && open(level, check.above(2)) ? check : null;
        }
        return null;
    }

    private static Shaft carve(ServerLevel level, BlockPos ground) {
        Shaft shaft = new Shaft(ground, level.getGameTime() + LIFETIME);
        int bottom = Math.max(level.getMinBuildHeight() + 1, ground.getY() - SHAFT_DEPTH);
        for (int dx = -HALF_X; dx <= HALF_X; dx++) {
            for (int dz = -NEG_Z; dz <= POS_Z; dz++) {
                for (int y = ground.getY(); y >= bottom; y--) {
                    BlockPos pos = new BlockPos(ground.getX() + dx, y, ground.getZ() + dz);
                    if (!level.hasChunkAt(pos)) break;
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0) continue;
                    shaft.positions.add(pos.asLong());
                    shaft.states.add(state);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    // the top layer breaks with debris, so the floor visibly goes (the fall effect darkens the screen fast)
                    if (y == ground.getY()) {
                        level.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, state),
                                pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.08);
                    }
                }
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, ground.getX() + 0.5, ground.getY() + 0.5, ground.getZ() + 0.5,
                40, 0.6, 0.4, 0.6, 0.02);
        level.playSound(null, ground, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.8F, 0.6F);
        return shaft;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        if (now % 10 != 0) return;
        Data data = Data.get(server);
        if (data.shafts.isEmpty()) return;
        RandomSource random = level.getRandom();
        for (Iterator<Shaft> it = data.shafts.iterator(); it.hasNext(); ) {
            Shaft shaft = it.next();
            if (shaft.expires > now) {
                for (int i = 0; i < 2; i++) {
                    double x = shaft.top.getX() + random.nextInt(HALF_X * 2 + 1) - HALF_X + 0.5;
                    double z = shaft.top.getZ() + random.nextInt(NEG_Z + POS_Z + 1) - NEG_Z + 0.5;
                    level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, shaft.top.getY() + 0.5, z, 1, 0.2, 0.3, 0.2, 0.01);
                }
                continue;
            }
            liftOut(level, shaft);
            if (heal(level, shaft)) it.remove();
            data.setDirty();
        }
    }

    private static void liftOut(ServerLevel level, Shaft shaft) {
        BlockPos top = shaft.top;
        AABB box = new AABB(top.getX() - HALF_X, level.getMinBuildHeight(), top.getZ() - NEG_Z,
                top.getX() + HALF_X + 1, top.getY() + 0.99, top.getZ() + POS_Z + 1);
        for (Entity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            entity.setPos(entity.getX(), top.getY() + 1.0, entity.getZ());
            entity.fallDistance = 0;
            entity.setDeltaMovement(0, 0, 0);
            entity.hurtMarked = true;
        }
    }

    private static boolean heal(ServerLevel level, Shaft shaft) {
        List<Long> keepPositions = new ArrayList<>();
        List<BlockState> keepStates = new ArrayList<>();
        for (int i = 0; i < shaft.positions.size(); i++) {
            BlockPos pos = BlockPos.of(shaft.positions.get(i));
            if (level.hasChunkAt(pos)) {
                level.setBlock(pos, shaft.states.get(i), FLAGS);
            } else {
                keepPositions.add(shaft.positions.get(i));
                keepStates.add(shaft.states.get(i));
            }
        }
        shaft.positions = keepPositions;
        shaft.states = keepStates;
        if (keepPositions.isEmpty()) LOG.info("[infdev] glitch patch at {} healed", shaft.top);
        return keepPositions.isEmpty();
    }

    // ---------------------------------------------------------------- the fall

    private static void watch(ServerPlayer player) {
        if (player.onGround() || player.getDeltaMovement().y >= 0 || player.isSpectator() || player.getAbilities().flying) return;
        if (!player.level().dimension().equals(Level.OVERWORLD) || FALL_ID.equals(Events.running(player))) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Data data = Data.get(server);
        if (data.shafts.isEmpty()) return;
        long now = player.level().getGameTime();
        BlockPos feet = player.blockPosition();
        for (Shaft shaft : data.shafts) {
            if (shaft.expires <= now) continue;
            int dx = feet.getX() - shaft.top.getX(), dz = feet.getZ() - shaft.top.getZ();
            if (dx < -HALF_X || dx > HALF_X || dz < -NEG_Z || dz > POS_Z || feet.getY() > shaft.top.getY() + 1) continue;
            LOG.info("[infdev] {} fell into the glitch patch at {}", player.getName().getString(), shaft.top);
            Events.start(player, FALL_ID);
            return;
        }
    }

    static final class Fall implements QuickEvent {
        @Override public String id() { return FALL_ID; }

        @Override
        public void start(ServerPlayer player) {
            EventNet.fx(player, "infdev_fall", FALL_FX_TICKS);
        }

        @Override
        public boolean tick(ServerPlayer player, int age) {
            MinecraftServer server = player.getServer();
            if (server != null && player.fallDistance >= FALL_DEPTH) {
                server.execute(() -> arrive(player));
                return true;
            }
            if (age > 200 || (age > 10 && player.onGround())) {
                EventNet.stopFx(player); // landed on something, or never fell far: no move
                return true;
            }
            return false;
        }
    }

    private static void arrive(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (!player.isAlive() || !player.level().dimension().equals(Level.OVERWORLD)) return;
        boolean m4kb = player.getRandom().nextBoolean(); // the old roll: half the falls end in m4kb_world
        ResourceKey<Level> target = m4kb ? M4KB_WORLD : INFDEV;
        ServerLevel level = server == null ? null : server.getLevel(target);
        if (level == null) {
            LOG.warn("[infdev] dimension {} not found", target.location());
            EventNet.stopFx(player);
            return;
        }
        BlockPos at;
        if (m4kb) {
            BlockPos spawn = level.getSharedSpawnPos();
            level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
            at = new BlockPos(spawn.getX(), (int) Ground.openY(level, spawn.getX(), spawn.getZ(), spawn.getY()), spawn.getZ());
        } else {
            at = landing(level, player.getRandom());
        }
        if (Warp.place(player, level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5)) {
            player.setDeltaMovement(0, 0, 0);
            player.hurtMarked = true;
            EventNet.fx(player, "infdev_arrive", ARRIVE_FX_TICKS);
        } else {
            EventNet.stopFx(player);
        }
    }

    private static BlockPos landing(ServerLevel level, RandomSource random) {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos best = spawn;
        for (int attempt = 0; attempt < 12; attempt++) {
            int r = 10 + attempt * 6;
            int x = spawn.getX() + random.nextInt(2 * r + 1) - r, z = spawn.getZ() + random.nextInt(2 * r + 1) - r;
            if (x == 0 || z == 0) continue;
            level.getChunk(x >> 4, z >> 4);
            BlockPos feet = new BlockPos(x, (int) Ground.openY(level, x, z, 70), z);
            best = feet;
            if (level.getFluidState(feet).isEmpty() && level.getFluidState(feet.below()).isEmpty()) return feet;
        }
        return best;
    }

    // ---------------------------------------------------------------- saved patches

    private static final class Shaft {
        final BlockPos top;
        final long expires;
        List<Long> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();

        Shaft(BlockPos top, long expires) {
            this.top = top;
            this.expires = expires;
        }
    }

    private static final class Data extends SavedData {
        private static final String NAME = "archivist_glitch_patches";
        final List<Shaft> shafts = new ArrayList<>();

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        boolean near(BlockPos pos) {
            for (Shaft shaft : shafts) {
                if (shaft.top.distSqr(pos) < 100) return true;
            }
            return false;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
            ListTag list = tag.getList("shafts", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                Shaft shaft = new Shaft(BlockPos.of(entry.getLong("top")), entry.getLong("expires"));
                List<BlockState> palette = new ArrayList<>();
                ListTag paletteTag = entry.getList("palette", Tag.TAG_COMPOUND);
                for (int p = 0; p < paletteTag.size(); p++) palette.add(NbtUtils.readBlockState(blocks, paletteTag.getCompound(p)));
                long[] positions = entry.getLongArray("positions");
                int[] indexes = entry.getIntArray("indexes");
                for (int k = 0; k < positions.length && k < indexes.length; k++) {
                    if (indexes[k] < 0 || indexes[k] >= palette.size()) continue;
                    shaft.positions.add(positions[k]);
                    shaft.states.add(palette.get(indexes[k]));
                }
                data.shafts.add(shaft);
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Shaft shaft : shafts) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("top", shaft.top.asLong());
                entry.putLong("expires", shaft.expires);
                Map<BlockState, Integer> palette = new LinkedHashMap<>();
                int[] indexes = new int[shaft.states.size()];
                long[] positions = new long[shaft.positions.size()];
                for (int k = 0; k < positions.length; k++) {
                    positions[k] = shaft.positions.get(k);
                    indexes[k] = palette.computeIfAbsent(shaft.states.get(k), s -> palette.size());
                }
                ListTag paletteTag = new ListTag();
                for (BlockState state : palette.keySet()) paletteTag.add(NbtUtils.writeBlockState(state));
                entry.put("palette", paletteTag);
                entry.putLongArray("positions", positions);
                entry.putIntArray("indexes", indexes);
                list.add(entry);
            }
            tag.put("shafts", list);
            return tag;
        }
    }
}