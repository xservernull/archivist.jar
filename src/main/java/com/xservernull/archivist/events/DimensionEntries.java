// the ways into the dimensions that had no trigger yet (old portaltriggers).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.List;

final class DimensionEntries {
    private DimensionEntries() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceKey<Level> FAR_LANDS = dimension("far_lands");
    static final ResourceKey<Level> FAR_LANDS_KEY = FAR_LANDS;
    private static final ResourceKey<Level> BEDROCK_FAR_LANDS = dimension("bedrock_far_lands");
    private static final ResourceKey<Level> VILLLLAGE = dimension("villllage");
    private static final ResourceKey<Level> XOR_WORLD = dimension("xor_world");
    private static final ResourceKey<Level> NEVER_FORGET_YOU = dimension("neverforgetyou");
    private static final ResourceKey<Level> RD132011 = dimension("rd132011");

    private static final double LIBRARY_X = 22.5, LIBRARY_Y = 61, LIBRARY_Z = 9.5;

    static final int FLOOR_MARGIN = 1;
    private static final int CHECK_EVERY = 10;
    private static final int WATER_RANGE = 3;
    private static final String HOPE = "hope";

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) onBottom(player);
        });
        NeoForge.EVENT_BUS.addListener(DimensionEntries::onBreak);
        NeoForge.EVENT_BUS.addListener(DimensionEntries::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, DimensionEntries::onWater);
        NeoForge.EVENT_BUS.addListener(DimensionEntries::onBook);
        NeoForge.EVENT_BUS.addListener(DimensionEntries::onVoidFall);
    }

    // ---------------------------------------------------------------- far_lands / bedrock_far_lands

    private static void onBottom(ServerPlayer player) {
        if (player.tickCount % CHECK_EVERY != 0 || !player.isAlive() || player.isSpectator()) return;
        if (!player.level().dimension().equals(Level.OVERWORLD)) return;
        if (player.blockPosition().getY() > player.level().getMinBuildHeight() + FLOOR_MARGIN) return;
        ResourceKey<Level> to = player.getRandom().nextBoolean() ? FAR_LANDS : BEDROCK_FAR_LANDS;
        LOG.info("[entry] {} reached the bottom of the overworld, sending to {}", player.getName().getString(), to.location());
        later(player, to);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;
        if (player.level().dimension().equals(BEDROCK_FAR_LANDS)) return;
        if (!e.getState().is(ModBlocks.FAKE_BEDROCK.get())) return;
        LOG.info("[entry] {} broke fake bedrock, sending to bedrock_far_lands", player.getName().getString());
        later(player, BEDROCK_FAR_LANDS);
    }

    // ---------------------------------------------------------------- villllage

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof IronGolem) || e.getEntity().level().isClientSide) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer)) return;
        MinecraftServer server = killer.getServer();
        if (server == null) return;
        LOG.info("[entry] {} killed an iron golem, sending every player to villllage", killer.getName().getString());
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) later(player, VILLLLAGE);
    }

    // ---------------------------------------------------------------- xor_world

    private static void onWater(PlayerInteractEvent.RightClickBlock e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer player)) return;
        if (!e.getItemStack().is(Items.WATER_BUCKET) || player.level().dimension().equals(XOR_WORLD)) return;
        Direction face = e.getFace() == null ? Direction.UP : e.getFace();
        BlockPos clicked = e.getPos(), target = clicked.relative(face);
        AABB box = new AABB(target).inflate(WATER_RANGE + 2);
        for (PrimedTnt tnt : e.getLevel().getEntitiesOfClass(PrimedTnt.class, box)) {
            if (tnt.getOwner() != player || tnt.getFuse() <= 0 || tnt.isRemoved()) continue;
            if (!near(tnt.blockPosition(), target) && !near(tnt.blockPosition(), clicked)) continue;
            LOG.info("[entry] {} put out their TNT with water, sending to xor_world", player.getName().getString());
            later(player, XOR_WORLD);
            return;
        }
    }

    private static boolean near(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= WATER_RANGE
                && Math.abs(a.getY() - b.getY()) <= WATER_RANGE
                && Math.abs(a.getZ() - b.getZ()) <= WATER_RANGE;
    }

    // ---------------------------------------------------------------- neverforgetyou

    private static void onBook(PlayerInteractEvent.RightClickItem e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = e.getItemStack();
        if (!stack.is(Items.WRITTEN_BOOK)) return;
        if (player.level().dimension().location().getNamespace().equals(Archivist.MOD_ID)) return;
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        String author = content == null ? "" : content.author();
        if (HOPE.equals(author)) return;
        LOG.info("[entry] {} opened a written book, sending to neverforgetyou", player.getName().getString());
        later(player, NEVER_FORGET_YOU);
    }

    // ---------------------------------------------------------------- rd132011 void fall

    private static void onVoidFall(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.level().dimension() != RD132011) return;
        if (!e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        e.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        LOG.info("[entry] {} fell out of rd132011, sending to the library", player.getName().getString());
        server.execute(() -> {
            ServerLevel library = server.getLevel(Archivist.LIBRARY);
            if (library == null) {
                LOG.warn("[entry] dimension {} not found", Archivist.LIBRARY.location());
                return;
            }
            Warp.place(player, library, LIBRARY_X, LIBRARY_Y, LIBRARY_Z);
        });
    }

    // ---------------------------------------------------------------- the move

    static void later(ServerPlayer player, ResourceKey<Level> to) {
        MinecraftServer server = player.getServer();
        if (server != null) server.execute(() -> send(player, to));
    }

    private static void send(ServerPlayer player, ResourceKey<Level> to) {
        if (!player.isAlive() || player.isSpectator() || player.level().dimension().equals(to)) return;
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(to);
        if (level == null) {
            LOG.warn("[entry] dimension {} not found", to.location());
            return;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        double y = Ground.openY(level, spawn.getX(), spawn.getZ(), spawn.getY());
        Warp.place(player, level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path));
    }
}