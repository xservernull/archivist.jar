// contains reusable mob goals so entity classes can register common behaviors with less repeated code.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import java.util.function.BooleanSupplier;

final class Goals {
    private Goals() {}
    
    static Goal watch(Mob mob, float range) {
        return new LookAtPlayerGoal(mob, Player.class, range);
    }
    
    static Goal lookAround(Mob mob) {
        return new RandomLookAroundGoal(mob);
    }
    
    static Goal wander(PathfinderMob mob, double speed) {
        return new WaterAvoidingRandomStrollGoal(mob, speed);
    }
    
    static Goal wander(PathfinderMob mob, double speed, BooleanSupplier allowed) {
        return new WaterAvoidingRandomStrollGoal(mob, speed) {
            @Override
            public boolean canUse() { return allowed.getAsBoolean() && super.canUse(); }
        };
    }
    
    static Goal huntPlayers(Mob mob) {
        return new NearestAttackableTargetGoal<>(mob, Player.class, true);
    }
}
