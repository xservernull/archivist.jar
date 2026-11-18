// xor_world bit math: the world shows "a ^ b = ?" and you answer by sneaking that many times
// (press shift, release, press...).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;

final class XorBitMath implements QuickEvent {
    private static final String A = "tmp_xor_a", B = "tmp_xor_b", COUNT = "tmp_xor_count", PREV = "tmp_xor_prev", LAST = "tmp_xor_last";
    private static final int DURATION = 20 * 30, SETTLE = 40;

    @Override public String id() { return "xor_bit_math"; }

    @Override
    public void start(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        int a = 1 + player.getRandom().nextInt(7);
        int b = 1 + player.getRandom().nextInt(7);
        if (a == b) b = (b % 7) + 1;
        memory.setCooldown(A, a);
        memory.setCooldown(B, b);
        memory.setCooldown(COUNT, 0L);
        memory.setCooldown(PREV, player.isShiftKeyDown() ? 1L : 0L);
        memory.setCooldown(LAST, 0L);
        player.sendSystemMessage(Texts.tr("archivist.event.xor_bit_math.start", "§7%s ^ %s = ?  sneak your answer.", a, b));
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 0.9F);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        EventState.Memory memory = Events.memory(player);
        int a = (int) memory.cooldownUntil(A), b = (int) memory.cooldownUntil(B);
        int answer = a ^ b;
        long count = memory.cooldownUntil(COUNT);
        long now = player.level().getGameTime();
        boolean sneaking = player.isShiftKeyDown();
        boolean was = memory.cooldownUntil(PREV) == 1L;
        if (sneaking != was) {
            memory.setCooldown(PREV, sneaking ? 1L : 0L);
            if (sneaking) {
                count++;
                memory.setCooldown(COUNT, count);
                memory.setCooldown(LAST, now);
            }
        }
        if (age % 10 == 0) player.displayClientMessage(Component.literal("§7" + a + " ^ " + b + " = " + count), true);
        boolean settled = count > 0 && now - memory.cooldownUntil(LAST) >= SETTLE;
        if (count > answer || settled || age >= DURATION) {
            if (count == answer) {
                Gifts.give(player, Gifts.XOR_NOTES);
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.2F);
                player.sendSystemMessage(Texts.tr("archivist.event.xor_bit_math.win", "§7%s. correct.", answer));
            } else {
                MarkerHunt.effect(player, MobEffects.MOVEMENT_SLOWDOWN, 20 * 5, 1);
                player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.6F, 0.6F);
                player.sendSystemMessage(Texts.tr("archivist.event.xor_bit_math.fail", "§7wrong. it was %s.", answer));
            }
            return true;
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        for (String k : new String[] {A, B, COUNT, PREV, LAST}) memory.setCooldown(k, 0L);
    }
}
