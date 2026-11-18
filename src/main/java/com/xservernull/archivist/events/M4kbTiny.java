// m4kb_world "small but important": you shrink to a quarter of your size for 20 seconds, so
// the 4kb blocks look huge.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Sounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class M4kbTiny implements QuickEvent {
    private static final ResourceLocation SHRINK = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "m4kb_tiny");
    private static final double AMOUNT = -0.75;
    private static final int DURATION = 400;

    @Override public String id() { return "m4kb_tiny"; }

    @Override
    public void start(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null || scale.hasModifier(SHRINK)) return;
        scale.addTransientModifier(new AttributeModifier(SHRINK, AMOUNT, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.GLITCH.get(), SoundSource.PLAYERS, 0.4F, 2.0F);
        EventNet.fx(player, "infdev_arrive", 10);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        if (age < DURATION) return false;
        if (grow(player)) EventNet.fx(player, "infdev_arrive", 10);
        return true;
    }

    @Override
    public void stop(ServerPlayer player) {
        grow(player);
    }

    private static boolean grow(ServerPlayer player) {
        AttributeInstance scale = player.getAttribute(Attributes.SCALE);
        if (scale == null || !scale.hasModifier(SHRINK)) return false;
        scale.removeModifier(SHRINK);
        player.refreshDimensions();
        if (player.isAlive() && player.level() instanceof ServerLevel level && !level.noCollision(player)) {
            Warp.place(player, level, player.getX(), player.getY(), player.getZ());
        }
        return true;
    }
}
