// "find the odd block": one glowing block appears on the ground 12 to 24 blocks away with a
// pale beam above it.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.Texts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.OptionalInt;
import java.util.function.Consumer;

final class MarkerHunt implements QuickEvent {
    private static final int MIN_RANGE = 12, MAX_RANGE = 24, DURATION = 20 * 45;

    private final String id;
    private final Block block;
    private final String startKey, startText, winKey, winText;
    private final Consumer<ServerPlayer> reward;

    MarkerHunt(String id, Block block, String startText, String winText, Consumer<ServerPlayer> reward) {
        this.id = id;
        this.block = block;
        this.startKey = "archivist.event." + id + ".start";
        this.winKey = "archivist.event." + id + ".win";
        this.startText = startText;
        this.winText = winText;
        this.reward = reward;
    }

    @Override public String id() { return id; }

    private String key() { return "tmp_" + id; }

    @Override
    public void start(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        EventState.Memory memory = Events.memory(player);
        memory.setCooldown(key(), 0L);
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
            double dist = MIN_RANGE + player.getRandom().nextInt(MAX_RANGE - MIN_RANGE + 1);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * dist);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * dist);
            if (!Ground.isLoaded(level, x, z)) continue;
            OptionalInt y = Ground.standY(level, x, player.getBlockY() + 6, z);
            if (y.isEmpty()) continue;
            BlockPos pos = new BlockPos(x, y.getAsInt(), z);
            if (!level.getBlockState(pos).isAir() || Math.abs(pos.getY() - player.getBlockY()) > 12) continue;
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            memory.setCooldown(key(), pos.asLong());
            player.sendSystemMessage(Texts.tr(startKey, startText));
            level.playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 1.8F);
            return;
        }
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        long packed = Events.memory(player).cooldownUntil(key());
        if (packed == 0L) return true; // nowhere to put it
        BlockPos pos = BlockPos.of(packed);
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return age >= DURATION;
        BlockState now = level.getBlockState(pos);
        if (!now.is(block)) {
            level.sendParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 24, 0.4, 0.4, 0.4, 0.05);
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
            player.sendSystemMessage(Texts.tr(winKey, winText));
            reward.accept(player);
            return true;
        }
        if (age % 10 == 0) {
            level.sendParticles(player, ParticleTypes.END_ROD, true, pos.getX() + 0.5, pos.getY() + 5.0, pos.getZ() + 0.5,
                    10, 0.1, 5.0, 0.1, 0.0);
        }
        if (age % 20 == 0) {
            int meters = (int) Math.round(Vec3.atCenterOf(pos).distanceTo(player.position()));
            player.displayClientMessage(Component.literal("§7" + meters + "m"), true);
        }
        return age >= DURATION;
    }

    @Override
    public void stop(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        long packed = memory.cooldownUntil(key());
        memory.setCooldown(key(), 0L);
        if (packed == 0L) return;
        BlockPos pos = BlockPos.of(packed);
        if (player.level() instanceof ServerLevel level && level.isLoaded(pos) && level.getBlockState(pos).is(block)) {
            level.removeBlock(pos, false);
        }
    }

    static void effect(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int ticks, int amp) {
        player.addEffect(new MobEffectInstance(effect, ticks, amp));
    }
}
