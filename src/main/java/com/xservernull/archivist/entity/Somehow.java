// defines somehow, a fast hostile mob with a temporary stun state and recurring glitch sound.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import java.util.EnumSet;

public final class Somehow extends Monster {
    
    public static final int STUN_TICKS = 60;
    
    public static final int GLITCH_PERIOD = 10;

    private int stunTicks;

    public Somehow(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.65)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 50.0);
    }

    public boolean isStunned() { return stunTicks > 0; }
    
    public void stun() {
        stunTicks = STUN_TICKS;
        getNavigation().stop();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        if (stunTicks > 0) stunTicks--;
        // From the moment it spawns until it dies or despawns: glitch.ogg every half second.
        if (tickCount % GLITCH_PERIOD == 1) {
            level().playSound(null, getX(), getY(), getZ(), Sounds.GLITCH.get(), SoundSource.HOSTILE, 1.5F, 1.0F);
        }
    }
    
    private final class StunGoal extends Goal {
        StunGoal() { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        @Override public boolean canUse() { return stunTicks > 0; }

        @Override public boolean canContinueToUse() { return stunTicks > 0; }

        @Override public void start() { getNavigation().stop(); }

        @Override public void tick() { getNavigation().stop(); }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new StunGoal());
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(4, Goals.watch(this, 8.0F));
        targetSelector.addGoal(1, Goals.huntPlayers(this));
    }
}
