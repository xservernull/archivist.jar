// controls dinnerbone’s attack sequence, from chasing and winding up to striking and recovering.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

final class DinnerboneWindupGoal extends Goal {
    private static final int WINDUP = 24;
    private static final int STRIKE_AT = 12;
    private static final int RECOVER = 5;
    private static final double RANGE = 3.0;

    private final Dinnerbone mob;
    private final double speed;
    private int windup;
    private int recover;
    private boolean struck;

    DinnerboneWindupGoal(Dinnerbone mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        windup = 0;
        recover = 0;
        struck = false;
    }

    @Override
    public void stop() {
        windup = 0;
        recover = 0;
        struck = false;
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = mob.distanceToSqr(target);
        if (windup > 0) {
            mob.getNavigation().stop();
            if (!struck && WINDUP - windup >= STRIKE_AT) {
                struck = true;
                if (distance <= RANGE * RANGE * 1.4) mob.doHurtTarget(target);
            }
            if (--windup <= 0) recover = RECOVER;
            return;
        }
        if (recover > 0) {
            recover--;
            return;
        }
        if (distance <= RANGE * RANGE) {
            windup = WINDUP;
            struck = false;
            mob.getNavigation().stop();
            mob.startWindup();
            return;
        }
        mob.getNavigation().moveTo(target, speed);
    }
}
