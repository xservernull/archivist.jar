// the way into the aether (old aetherportal). the old glowstone frame is removed on purpose:
// it collided with the aether mod's own portal.


package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

final class AetherPortal {
    private AetherPortal() {}

    private static final int MIN_W = 2, MIN_H = 3, MAX_W = 21, MAX_H = 21;
    private static final int COOLDOWN_TICKS = 100;
    private static final String COOLDOWN = "aether_portal";

    private static final Predicate<BlockState> PORTAL_STONE = s -> s.is(ModBlocks.AETHERPORTALSTONE.get());

    private record Frame(BlockPos origin, Direction across, int width, int height) {
        BlockPos cell(int i, int j) {
            return origin.relative(across, i).above(j);
        }
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener(AetherPortal::onUse);
        NeoForge.EVENT_BUS.addListener(AetherPortal::onTick);
        NeoForge.EVENT_BUS.addListener(AetherPortal::onBreak);
    }

    // ---------------------------------------------------------------- triggers

    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || e.getFace() == null) return;
        ItemStack held = e.getItemStack();
        if (!held.is(Items.WATER_BUCKET)) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() == Archivist.AETHER) return;
        BlockPos clicked = e.getPos();
        BlockPos spot = clicked.relative(e.getFace());

        Frame stone = find(level, spot, PORTAL_STONE);
        if (stone == null && isOpen(level.getBlockState(clicked))) stone = find(level, clicked, PORTAL_STONE);
        if (stone != null) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            fill(level, stone);
            level.playSound(null, spot, SoundEvents.PORTAL_TRIGGER, SoundSource.BLOCKS, 0.5F, 0.5F);
            if (!player.isCreative()) spendBucket(player, e.getHand(), held);
            enter(player);
            return;
        }
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        ServerLevel level = player.serverLevel();
        if (level.dimension() == Archivist.AETHER) return;
        BlockPos feet = player.blockPosition();
        if (!level.getBlockState(feet).is(Blocks.WATER)) return;
        if (find(level, feet, PORTAL_STONE) != null) enter(player);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !PORTAL_STONE.test(e.getState())) return;
        for (Direction d : Direction.values()) {
            BlockPos next = e.getPos().relative(d);
            if (level.getBlockState(next).is(Blocks.WATER)) {
                drain(level, next, true);
                drain(level, next, false);
            }
        }
    }

    private static void enter(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        long now = player.level().getGameTime();
        if (now < memory.cooldownUntil(COOLDOWN)) return;
        memory.setCooldown(COOLDOWN, now + COOLDOWN_TICKS);
        AetherEntry.enter(player);
    }

    private static void spendBucket(ServerPlayer player, InteractionHand hand, ItemStack held) {
        held.shrink(1);
        ItemStack empty = new ItemStack(Items.BUCKET);
        if (held.isEmpty()) player.setItemInHand(hand, empty);
        else if (!player.getInventory().add(empty)) player.drop(empty, false);
    }

    // ---------------------------------------------------------------- frames

    private static boolean isOpen(BlockState s) {
        return s.isAir() || s.is(Blocks.WATER);
    }

    private static Frame find(Level level, BlockPos start, Predicate<BlockState> border) {
        for (Direction across : new Direction[]{Direction.EAST, Direction.SOUTH}) {
            Frame f = measure(level, start, across, border);
            if (f != null) return f;
        }
        return null;
    }

    private static Frame measure(Level level, BlockPos start, Direction across, Predicate<BlockState> border) {
        if (!isOpen(level.getBlockState(start))) return null;
        BlockPos corner = start;
        for (int n = 0; isOpen(level.getBlockState(corner.below())); n++) {
            if (n > MAX_H) return null;
            corner = corner.below();
        }
        for (int n = 0; isOpen(level.getBlockState(corner.relative(across.getOpposite()))); n++) {
            if (n > MAX_W) return null;
            corner = corner.relative(across.getOpposite());
        }
        int w = 0;
        while (isOpen(level.getBlockState(corner.relative(across, w)))) {
            if (++w > MAX_W) return null;
        }
        int h = 0;
        while (isOpen(level.getBlockState(corner.above(h)))) {
            if (++h > MAX_H) return null;
        }
        if (w < MIN_W || h < MIN_H) return null;
        Frame f = new Frame(corner, across, w, h);
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                if (!isOpen(level.getBlockState(f.cell(i, j)))) return null;
            }
        }
        for (int i = 0; i < w; i++) {
            if (!border.test(level.getBlockState(f.cell(i, -1))) || !border.test(level.getBlockState(f.cell(i, h)))) return null;
        }
        for (int j = 0; j < h; j++) {
            if (!border.test(level.getBlockState(f.cell(-1, j))) || !border.test(level.getBlockState(f.cell(w, j)))) return null;
        }
        return f;
    }

    private static void fill(ServerLevel level, Frame f) {
        for (int i = 0; i < f.width(); i++) {
            for (int j = 0; j < f.height(); j++) {
                BlockPos p = f.cell(i, j);
                if (level.getBlockState(p).isAir()) level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
            }
        }
    }

    private static void drain(ServerLevel level, BlockPos from, boolean alongX) {
        Direction side = alongX ? Direction.EAST : Direction.SOUTH;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(from);
        while (!open.isEmpty()) {
            BlockPos p = open.poll();
            if (!level.getBlockState(p).is(Blocks.WATER) || !seen.add(p)) continue;
            if (seen.size() > MAX_W * MAX_H) return;
            open.add(p.relative(side));
            open.add(p.relative(side.getOpposite()));
            open.add(p.above());
            open.add(p.below());
        }
        for (BlockPos p : seen) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
    }
}