// handles the copy mobs’ behavior of fleeing from nearby players when they have no target to attack.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

final class CopyFleeGoal extends Goal {
    private static final double RADIUS = 12.0;
    private static final double KEEP = 4.0;
    private static final double SPEED = 1.3;

    private final CopyMob mob;
    private Player from;

    CopyFleeGoal(CopyMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        UUID id = mob.targetPlayer();
        Player bound = id == null ? null : mob.level().getPlayerByUUID(id);
        if (bound != null && mob.distanceTo(bound) < RADIUS) {
            from = bound;
            return true;
        }
        from = mob.level().getNearestPlayer(mob, RADIUS);
        return from != null;
    }

    @Override
    public boolean canContinueToUse() {
        return from != null && from.isAlive() && mob.distanceTo(from) < RADIUS + KEEP;
    }

    @Override
    public void stop() {
        from = null;
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (from == null) return;
        double dx = mob.getX() - from.getX();
        double dz = mob.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.01) return;
        mob.getNavigation().moveTo(mob.getX() + dx / length * 16.0, mob.getY(), mob.getZ() + dz / length * 16.0, SPEED);
    }
}
