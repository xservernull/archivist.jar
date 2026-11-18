// coordinates group alerts so nearby them entities can share or acquire a player target.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.List;

final class ThemAlert extends Goal {
    private static final double RADIUS = 64.0;
    private static final int CHECK_INTERVAL = 10;

    private final Them mob;
    private int timer;

    ThemAlert(Them mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        return ++timer >= CHECK_INTERVAL;
    }

    @Override
    public boolean canContinueToUse() { return false; }

    @Override
    public void start() {
        timer = 0;
        List<Them> near = mob.level().getEntitiesOfClass(Them.class, mob.getBoundingBox().inflate(RADIUS),
                other -> other != mob && other.isAlive() && other.distanceToSqr(mob) <= RADIUS * RADIUS);
        if (mob.getTarget() instanceof Player player) {
            for (Them other : near) {
                if (other.getTarget() == null) other.setTarget(player);
            }
            return;
        }
        for (Them other : near) {
            if (other.getTarget() instanceof Player known) {
                mob.setTarget(known);
                return;
            }
        }
    }
}
