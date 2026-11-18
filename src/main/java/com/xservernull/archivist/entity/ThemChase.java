// implements them’s pursuit behavior, predicting player movement and handling stuck pathfinding.

package com.xservernull.archivist.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;

final class ThemChase extends Goal {
    private static final int PATH_INTERVAL = 4;
    private static final int STUCK_LIMIT = 15;
    private static final int SAMPLE_INTERVAL = 3;
    private static final int SCAN_INTERVAL = 10;
    private static final double REACH_SQ = 2.8 * 2.8;
    private static final int ATTACK_COOLDOWN = 15;
    private static final double GIVE_UP = 160.0;
    private static final double SCAN_RANGE = 128.0;
    private static final double SPEED = 1.0;

    private final PathfinderMob mob;
    private Player target;
    private int pathTimer, attackCooldown, stuckCounter, lastScanTick = -1;
    private Vec3 lastMobPos = Vec3.ZERO;
    private Vec3 lastSample, velocity = Vec3.ZERO;
    private int sampleTimer;

    ThemChase(PathfinderMob mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private static boolean valid(Player player) {
        return player != null && player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    @Override
    public boolean canUse() {
        LivingEntity current = mob.getTarget();
        if (current instanceof Player player && valid(player)) {
            target = player;
            return true;
        }
        if (lastScanTick != -1 && mob.tickCount - lastScanTick < SCAN_INTERVAL) return false;
        lastScanTick = mob.tickCount;
        Player nearest = mob.level().getEntitiesOfClass(Player.class, mob.getBoundingBox().inflate(SCAN_RANGE),
                        ThemChase::valid).stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(mob)))
                .orElse(null);
        if (nearest == null) return false;
        target = nearest;
        mob.setTarget(nearest);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (target == null || !target.isAlive() || target.isSpectator()) return false;
        if (mob.distanceToSqr(target) > GIVE_UP * GIVE_UP) return false;
        return mob.getTarget() != null;
    }

    @Override
    public void start() {
        mob.setSprinting(true);
        pathTimer = 0;
        stuckCounter = 0;
        attackCooldown = 0;
        sampleTimer = 0;
        lastMobPos = mob.position();
        lastSample = target.position();
        velocity = Vec3.ZERO;
    }

    @Override
    public void stop() {
        mob.setSprinting(false);
        mob.getNavigation().stop();
        stuckCounter = 0;
        target = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void tick() {
        if (target == null) return;
        if (attackCooldown > 0) attackCooldown--;
        mob.getLookControl().setLookAt(target, 30.0F, mob.getMaxHeadXRot());

        if (++sampleTimer >= SAMPLE_INTERVAL) {
            sampleTimer = 0;
            Vec3 now = target.position();
            velocity = now.subtract(lastSample).scale(0.4).add(velocity.scale(0.6));
            lastSample = now;
        }

        Vec3 here = mob.position();
        if (here.distanceToSqr(lastMobPos) < 0.01) stuckCounter++;
        else stuckCounter = 0;
        lastMobPos = here;

        boolean sideStep = stuckCounter >= STUCK_LIMIT;
        if (sideStep || ++pathTimer >= PATH_INTERVAL) {
            pathTimer = 0;
            Vec3 dest;
            if (sideStep) {
                stuckCounter = 0;
                Vec3 side = new Vec3(-target.getDeltaMovement().z, 0, target.getDeltaMovement().x);
                dest = target.position().add(side.scale(2.0 + mob.getRandom().nextDouble() * 3.0));
            } else {
                dest = target.position().add(velocity.scale(3.0));
            }
            mob.getNavigation().moveTo(dest.x, dest.y, dest.z, SPEED);
        }

        if (attackCooldown == 0 && mob.distanceToSqr(target) <= REACH_SQ) {
            mob.doHurtTarget(target);
            attackCooldown = ATTACK_COOLDOWN;
        }
        if (!mob.isSprinting()) mob.setSprinting(true);
    }
}
