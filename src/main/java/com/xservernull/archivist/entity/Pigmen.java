// defines the pigmen entity and its basic wandering behavior.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.level.Level;

public final class Pigmen extends PathfinderMob {
    public Pigmen(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, Goals.wander(this, 1.0));
    }
}
