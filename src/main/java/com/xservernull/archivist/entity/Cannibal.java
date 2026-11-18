// adds the behavior that makes nearby overworld animals attack one another when the config is enabled.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.ArchivistConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.EnumSet;
import java.util.List;


@EventBusSubscriber(modid = "archivist")
public final class Cannibal {
    private Cannibal() {}

    private static final double PLAYER_RANGE = 20.0;
    private static final double SCAN_RANGE = 16.0;
    private static final long SCAN_EVERY = 20L;

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!event.getLevel().dimension().equals(Level.OVERWORLD)) return;
        if (!(event.getEntity() instanceof Animal animal)) return;
        if (!(animal instanceof Wolf || animal instanceof Fox || animal instanceof PolarBear
                || animal instanceof Cow || animal instanceof Sheep || animal instanceof Pig
                || animal instanceof Chicken || animal instanceof Rabbit
                || animal instanceof Horse || animal instanceof Llama)) return;
        boolean has = animal.goalSelector.getAvailableGoals().stream()
                .anyMatch(w -> w.getGoal() instanceof Attack);
        if (has) return;
        animal.targetSelector.addGoal(1, new Pick(animal));
        animal.goalSelector.addGoal(3, new Attack(animal, 1.2, true));
    }

    private static boolean isPet(LivingEntity e) {
        return e instanceof TamableAnimal t && t.isTame();
    }

    
    public static final class Pick extends TargetGoal {
        private final PathfinderMob animal;
        private LivingEntity found;
        private long nextScan;

        public Pick(PathfinderMob animal) {
            super(animal, true, false);
            this.animal = animal;
            this.setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!ArchivistConfig.cannibalism() || isPet(this.animal)) return false;
            long now = this.animal.level().getGameTime();
            if (now < this.nextScan) return false;
            this.nextScan = now + SCAN_EVERY;
            if (this.animal.level().getNearestPlayer(this.animal, PLAYER_RANGE) == null) return false;
            this.found = nearestAnimal();
            return this.found != null;
        }

        @Override
        public void start() {
            this.animal.setTarget(this.found);
            super.start();
        }

        @Override
        public boolean canContinueToUse() {
            if (!ArchivistConfig.cannibalism()) return false;
            if (this.animal.level().getNearestPlayer(this.animal, PLAYER_RANGE) == null) return false;
            LivingEntity t = this.animal.getTarget();
            return t != null && t.isAlive();
        }

        private LivingEntity nearestAnimal() {
            AABB box = this.animal.getBoundingBox().inflate(SCAN_RANGE, 4.0, SCAN_RANGE);
            List<Animal> near = this.animal.level().getEntitiesOfClass(Animal.class, box,
                    a -> a != this.animal && a.isAlive() && !a.isDeadOrDying() && !isPet(a));
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (Animal a : near) {
                double d = this.animal.distanceToSqr(a);
                if (d < bestDist) {
                    bestDist = d;
                    best = a;
                }
            }
            return best;
        }
    }

    
    public static final class Attack extends Goal {
        private final PathfinderMob mob;
        private final double speed;
        private final boolean followUnseen;
        private Path path;
        private double lastX, lastY, lastZ;
        private int repath;
        private int cooldown;
        private long lastCheck;

        public Attack(PathfinderMob mob, double speed, boolean followUnseen) {
            this.mob = mob;
            this.speed = speed;
            this.followUnseen = followUnseen;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!ArchivistConfig.cannibalism() || isPet(this.mob)) return false;
            long now = this.mob.level().getGameTime();
            if (now - this.lastCheck < 20L) return false;
            this.lastCheck = now;
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) return false;
            this.path = this.mob.getNavigation().createPath(target, 0);
            if (this.path != null) return true;
            return reachSqr(target) >= this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
        }

        @Override
        public boolean canContinueToUse() {
            if (!ArchivistConfig.cannibalism()) return false;
            if (this.mob.level().getNearestPlayer(this.mob, PLAYER_RANGE) == null) return false;
            LivingEntity target = this.mob.getTarget();
            if (target == null || !target.isAlive()) return false;
            if (!this.followUnseen) {
                return !this.mob.getNavigation().isDone()
                        || this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ()) <= reachSqr(target);
            }
            return this.mob.isWithinRestriction(target.blockPosition());
        }

        @Override
        public void start() {
            this.mob.getNavigation().moveTo(this.path, this.speed);
            this.mob.setAggressive(true);
            this.repath = 0;
            this.cooldown = 0;
        }

        @Override
        public void stop() {
            this.mob.getNavigation().stop();
            this.mob.setAggressive(false);
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) return;
            this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distSq = this.mob.distanceToSqr(target.getX(), target.getY(), target.getZ());
            this.repath = Math.max(this.repath - 1, 0);
            if ((this.followUnseen || this.mob.getSensing().hasLineOfSight(target))
                    && this.repath <= 0
                    && ((this.lastX == 0 && this.lastY == 0 && this.lastZ == 0)
                    || target.distanceToSqr(this.lastX, this.lastY, this.lastZ) >= 1.0
                    || this.mob.getRandom().nextFloat() < 0.05F)) {
                this.lastX = target.getX();
                this.lastY = target.getY();
                this.lastZ = target.getZ();
                this.repath = 4 + this.mob.getRandom().nextInt(7);
                if (distSq > 1024.0) this.repath += 10;
                else if (distSq > 256.0) this.repath += 5;
                if (!this.mob.getNavigation().moveTo(target, this.speed)) this.repath += 15;
            }
            this.cooldown = Math.max(this.cooldown - 1, 0);
            if (distSq <= reachSqr(target) && this.cooldown <= 0) {
                this.cooldown = 20;
                target.hurt(this.mob.damageSources().mobAttack(this.mob), damage());
            }
        }

        private double reachSqr(LivingEntity target) {
            return this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 2.0F + target.getBbWidth();
        }

        private float damage() {
            if (this.mob instanceof Wolf) return 4.0F;
            if (this.mob instanceof PolarBear) return 6.0F;
            if (this.mob instanceof Fox) return 2.0F;
            if (this.mob instanceof Cow || this.mob instanceof Sheep || this.mob instanceof Pig) return 3.0F;
            if (this.mob instanceof Chicken || this.mob instanceof Rabbit) return 1.0F;
            if (this.mob instanceof Horse || this.mob instanceof Llama) return 4.0F;
            return 2.0F;
        }
    }
}