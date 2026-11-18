// defines an indestructible door entity that serves as a visual prop rather than an exit.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class FakeDoor extends PathfinderMob {
    public FakeDoor(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
        setInvulnerable(true);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide) player.sendSystemMessage(Texts.tr("archivist.msg.door", "this is not your exit"));
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean isPushable() { return false; }
}
