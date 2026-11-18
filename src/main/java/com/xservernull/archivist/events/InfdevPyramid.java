// the brick pyramid of archivist:infdev20100227 and its chest (old infdevpyramidchest and the
// old brick_pyramid_def placement), with the flow changed to take or leave the axe (user,

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Somehow;
import com.xservernull.archivist.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalInt;

final class InfdevPyramid {
    private InfdevPyramid() {}

    private static final Logger LOG = LogUtils.getLogger();

    static final String WAVES_ID = "pyramid_waves";

    private static final ResourceLocation PYRAMID = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "brickpyramid");

    private static final int CHEST_X = 33, CHEST_Y = 2, CHEST_Z = 32;

    private static final int DISTANCE = 80;

    private static final int FIRST_WAVE = 60, WAVE_EVERY = 600, PER_WAVE = 3, MAX_ALIVE = 9;
    private static final double RING_MIN = 12.0, RING_MAX = 17.0, LEAVE = 48.0;
    private static final int DARKNESS_TICKS = 60, GREY_TICKS = 80;

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final Deque<ServerLevel> QUEUE = new ArrayDeque<>();

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && InfdevWorld.isInfdev(player.level())) check(player.serverLevel());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && InfdevWorld.isInfdev(player.level())) check(player.serverLevel());
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> {
            ServerLevel level = QUEUE.poll();
            if (level != null) build(level);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> QUEUE.clear());
        NeoForge.EVENT_BUS.addListener(InfdevPyramid::onClose);
    }

    // ---------------------------------------------------------------- placing it

    private static void check(ServerLevel level) {
        if (!QUEUE.isEmpty() || Data.get(level.getServer()).placed) return;
        QUEUE.add(level);
    }

    private record Spot(int x, int y, int z, int score) {}

    private static void build(ServerLevel level) {
        Data data = Data.get(level.getServer());
        if (data.placed) return;
        StructureTemplate template = level.getStructureManager().get(PYRAMID).orElse(null);
        if (template == null) {
            LOG.warn("[infdev] structure {} not found (data/{}/structure/{}.nbt), skipped", PYRAMID, PYRAMID.getNamespace(), PYRAMID.getPath());
            return;
        }
        Vec3i size = template.getSize();
        Spot spot = pick(level, size);
        BlockPos origin = new BlockPos(spot.x(), spot.y(), spot.z());
        // chunks of the whole box, then cut the hills inside it down to the slab
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + size.getX() - 1) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + size.getZ() - 1) >> 4; cz++) level.getChunk(cx, cz);
        }
        for (int x = origin.getX(); x < origin.getX() + size.getX(); x++) {
            for (int z = origin.getZ(); z < origin.getZ() + size.getZ(); z++) {
                int top = Ground.surface(level, x, z).getY();
                for (int y = origin.getY() + 1; y <= top; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), FLAGS);
            }
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        if (!template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2)) {
            LOG.warn("[infdev] pyramid was not placed at {}", origin);
            return;
        }
        BlockPos chest = origin.offset(CHEST_X, CHEST_Y, CHEST_Z);
        if (level.getBlockState(chest.below()).isAir()) level.setBlock(chest.below(), Blocks.BRICKS.defaultBlockState(), 2);
        if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity)) level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
            if (box.isEmpty()) box.setItem(0, new ItemStack(ModItems.IRON_AXE_ARCHIVE.get())); // the template's item did not load
        } else {
            LOG.warn("[infdev] no chest at {}", chest);
        }
        data.placed = true;
        data.chest = chest;
        data.setDirty();
        LOG.info("[infdev] pyramid placed at {} (chest {}, spot score {})", origin, chest, spot.score());
    }

    private static Spot pick(ServerLevel level, Vec3i size) {
        BlockPos spawn = level.getSharedSpawnPos();
        Spot best = null;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            int ox = spawn.getX() + (int) Math.round(Math.cos(angle) * DISTANCE) - size.getX() / 2;
            int oz = spawn.getZ() + (int) Math.round(Math.sin(angle) * DISTANCE) - size.getZ() / 2;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0;
            for (int sx = 0; sx < 3; sx++) {
                for (int sz = 0; sz < 3; sz++) {
                    int x = ox + sx * (size.getX() - 1) / 2, z = oz + sz * (size.getZ() - 1) / 2;
                    level.getChunk(x >> 4, z >> 4);
                    BlockPos top = Ground.surface(level, x, z);
                    if (!level.getFluidState(top).isEmpty()) wet++;
                    min = Math.min(min, top.getY());
                    max = Math.max(max, top.getY());
                }
            }
            boolean axis = (ox <= 0 && ox + size.getX() > 0) || (oz <= 0 && oz + size.getZ() > 0);
            int score = (max - min) + wet * 40 + (axis ? 25 : 0);
            if (best == null || score < best.score()) best = new Spot(ox, min - 1, oz, score);
        }
        return best;
    }

    // ---------------------------------------------------------------- the choice

    private static void onClose(PlayerContainerEvent.Close e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !(e.getContainer() instanceof ChestMenu menu)) return;
        if (!(menu.getContainer() instanceof ChestBlockEntity chest) || !(chest.getLevel() instanceof ServerLevel level)) return;
        if (!InfdevWorld.isInfdev(level)) return;
        Data data = Data.get(level.getServer());
        if (!data.placed || !chest.getBlockPos().equals(data.chest) || hasAxe(chest)) return;
        if (WAVES_ID.equals(Events.running(player))) return;
        LOG.info("[infdev] {} took the axe", player.getName().getString());
        Events.start(player, WAVES_ID);
    }

    private static boolean hasAxe(ChestBlockEntity chest) {
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(ModItems.IRON_AXE_ARCHIVE.get())) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- the waves

    static final class Waves implements QuickEvent {
        @Override public String id() { return WAVES_ID; }

        @Override
        public void start(ServerPlayer player) {
            discardTagged(player); // leftovers of a crash or a reload
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0));
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 0.5F);
        }

        @Override
        public boolean tick(ServerPlayer player, int age) {
            MinecraftServer server = player.getServer();
            if (server == null || !InfdevWorld.isInfdev(player.level())) return true;
            Data data = Data.get(server);
            if (!data.placed) return true;
            BlockPos chest = data.chest;
            double dx = player.getX() - (chest.getX() + 0.5), dz = player.getZ() - (chest.getZ() + 0.5);
            if (dx * dx + dz * dz > LEAVE * LEAVE) return true;
            if (age >= FIRST_WAVE && (age - FIRST_WAVE) % WAVE_EVERY == 0) wave(player, chest);
            return false;
        }

        @Override
        public void stop(ServerPlayer player) {
            discardTagged(player);
        }
    }

    private static String tag(ServerPlayer player) {
        return "archivist_somehow_" + player.getUUID();
    }

    private static void wave(ServerPlayer player, BlockPos chest) {
        ServerLevel level = player.serverLevel();
        String tag = tag(player);
        AABB box = new AABB(chest).inflate(LEAVE * 2);
        int alive = level.getEntities(Mobs.SOMEHOW.get(), box, s -> s.getTags().contains(tag)).size();
        EventNet.fx(player, "greyscale", GREY_TICKS);
        int spawned = 0;
        for (int i = 0; i < PER_WAVE && alive + spawned < MAX_ALIVE; i++) {
            double angle = Math.PI * 2 / PER_WAVE * i + player.getRandom().nextDouble() * 0.4;
            double radius = RING_MIN + player.getRandom().nextDouble() * (RING_MAX - RING_MIN);
            int x = Mth.floor(chest.getX() + 0.5 + Math.cos(angle) * radius);
            int z = Mth.floor(chest.getZ() + 0.5 + Math.sin(angle) * radius);
            if (!Ground.isLoaded(level, x, z)) continue;
            // walk down from just above the chest: inside the pyramid that is its floor, not its roof
            OptionalInt y = Ground.standY(level, x, chest.getY() + 2, z);
            if (y.isEmpty()) continue;
            Somehow somehow = Mobs.SOMEHOW.get().create(level);
            if (somehow == null) continue;
            somehow.moveTo(x + 0.5, y.getAsInt(), z + 0.5, player.getRandom().nextFloat() * 360.0F, 0.0F);
            somehow.addTag(tag);
            somehow.setTarget(player);
            somehow.setSprinting(true);
            level.addFreshEntity(somehow);
            spawned++;
        }
        LOG.info("[infdev] wave for {}: {} Somehow ({} alive before)", player.getName().getString(), spawned, alive);
    }

    private static void discardTagged(ServerPlayer player) {
        String tag = tag(player);
        MinecraftServer server = player.getServer();
        List<ServerLevel> levels = new ArrayList<>();
        levels.add(player.serverLevel());
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                if (InfdevWorld.isInfdev(level) && !levels.contains(level)) levels.add(level);
            }
        }
        List<Entity> found = new ArrayList<>();
        for (ServerLevel level : levels) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Somehow && e.getTags().contains(tag)) found.add(e);
            }
        }
        found.forEach(Entity::discard);
    }

    // ---------------------------------------------------------------- saved data

    static java.util.Optional<BlockPos> chest(MinecraftServer server) {
        Data data = Data.get(server);
        return data.placed ? java.util.Optional.of(data.chest) : java.util.Optional.empty();
    }

    private static final class Data extends SavedData {
        private static final String NAME = "archivist_infdev_pyramid";
        boolean placed;
        BlockPos chest = BlockPos.ZERO;

        static Data get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.placed = tag.getBoolean("placed");
            data.chest = BlockPos.of(tag.getLong("chest"));
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("placed", placed);
            tag.putLong("chest", chest.asLong());
            return tag;
        }
    }
}