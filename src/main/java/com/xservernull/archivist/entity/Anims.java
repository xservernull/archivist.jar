// provides shared geckolib animation helpers for entity idle, movement, and one-shot animations.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

final class Anims {
    private Anims() {}
    
    static RawAnimation loop(Entity entity, String state) {
        return RawAnimation.begin().thenLoop(name(entity, state));
    }
    
    static RawAnimation once(Entity entity, String state) {
        return RawAnimation.begin().thenPlay(name(entity, state));
    }
    
    static boolean moving(Entity entity) {
        return entity.getDeltaMovement().horizontalDistanceSqr() > 0.001D;
    }
    
    static <T extends Entity & GeoAnimatable> AnimationController<T> locomotion(T entity, int transitionTicks) {
        RawAnimation idle = loop(entity, "idle");
        RawAnimation walk = loop(entity, "walk");
        return new AnimationController<>(entity, "locomotion", transitionTicks,
                state -> state.setAndContinue(moving(entity) ? walk : idle));
    }
    
    static <T extends Entity & GeoAnimatable> AnimationController<T> walking(T entity, int transitionTicks) {
        RawAnimation walk = loop(entity, "walk");
        return new AnimationController<>(entity, "locomotion", transitionTicks,
                state -> moving(entity) ? state.setAndContinue(walk) : PlayState.STOP);
    }
    
    static <T extends Entity & GeoAnimatable> AnimationController<T> idle(T entity, int transitionTicks) {
        RawAnimation idle = loop(entity, "idle");
        return new AnimationController<>(entity, "main", transitionTicks, state -> state.setAndContinue(idle));
    }

    private static String name(Entity entity, String state) {
        return "animation." + Mobs.id(entity) + "." + state;
    }
}
