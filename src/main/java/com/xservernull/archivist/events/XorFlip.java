// xor_world flip: gravity flips sign for up to 6 seconds and you drift up through the cube.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Sounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class XorFlip implements QuickEvent {
    private static final ResourceLocation FLIP = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "xor_flip");
    private static final double AMOUNT = -1.0;
    private static final int SIZE = 64, PERIOD = 88, DURATION = 20 * 6, ROOF = 3;

    @Override public String id() { return "xor_flip"; }

    @Override
    public void start(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null || gravity.hasModifier(FLIP)) return;
        gravity.addTransientModifier(new AttributeModifier(FLIP, AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 0.5F);
        EventNet.fx(player, "infdev_arrive", 10);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        boolean top = Math.floorMod(player.getBlockY(), PERIOD) >= SIZE - ROOF;
        if (age < DURATION && !top) return false;
        if (drop(player)) EventNet.fx(player, "infdev_arrive", 10);
        return true;
    }

    @Override
    public void stop(ServerPlayer player) {
        drop(player);
    }

    private static boolean drop(ServerPlayer player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        if (gravity == null || !gravity.hasModifier(FLIP)) return false;
        gravity.removeModifier(FLIP);
        player.fallDistance = 0;
        return true;
    }
}
