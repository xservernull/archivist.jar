// handles them’s prayer behavior around the far lands temple while the global prayer event is active.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Ground;
import com.xservernull.archivist.events.FarLandsTemple;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

final class ThemPray extends Goal {
    private static final double SPEED = 0.8, WANDER_SPEED = 0.6, ARRIVE = 2.5, MAX_DISTANCE = 140.0;
    private static final int REPATH_TICKS = 20, WANDER_BLOCKS = 24;

    private final Them mob;
    private Vec3 spot;
    private int repath;

    ThemPray(Them mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        return FarLandsTemple.built(level.getServer())
                && FarLandsTemple.praying(level)
                && mob.position().distanceToSqr(FarLandsTemple.centre()) <= MAX_DISTANCE * MAX_DISTANCE;
    }

    @Override
    public void start() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        spot = FarLandsTemple.spot(level, mob.getUUID());
        mob.setTarget(null);
        mob.setSprinting(false);
        mob.getNavigation().moveTo(spot.x, spot.y, spot.z, SPEED);
        repath = REPATH_TICKS;
    }

    @Override
    public void tick() {
        if (spot == null) return;
        mob.setTarget(null); // the target goal picks a player again every tick
        if (mob.isSprinting()) mob.setSprinting(false);
        if (mob.position().distanceToSqr(spot.x, mob.getY(), spot.z) <= ARRIVE * ARRIVE) {
            mob.getNavigation().stop();
            Vec3 centre = FarLandsTemple.centre();
            mob.getLookControl().setLookAt(centre.x, centre.y + 6.0, centre.z, 30.0F, 30.0F);
        } else if (--repath <= 0 || mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(spot.x, spot.y, spot.z, SPEED);
            repath = REPATH_TICKS;
        }
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
        spot = null;
        if (!(mob.level() instanceof ServerLevel level) || FarLandsTemple.praying(level)) return; // pushed aside, not finished
        Vec3 away = mob.position().subtract(FarLandsTemple.centre());
        away = new Vec3(away.x, 0.0, away.z);
        away = away.lengthSqr() < 1.0 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
        double x = mob.getX() + away.x * WANDER_BLOCKS + (mob.getRandom().nextDouble() - 0.5) * 12.0;
        double z = mob.getZ() + away.z * WANDER_BLOCKS + (mob.getRandom().nextDouble() - 0.5) * 12.0;
        double y = Ground.openY(level, (int) Math.floor(x), (int) Math.floor(z), mob.getY());
        mob.getNavigation().moveTo(x, y, z, WANDER_SPEED);
    }
}
