// defines a statue that remains still until activated, then hunts the nearest player.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class Statue extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> HUNTING =
            SynchedEntityData.defineId(Statue.class, EntityDataSerializers.BOOLEAN);

    private static final int FOOTSTEP_MIN = 20 * 10;
    private static final int FOOTSTEP_MAX = 20 * 30;

    private int footstepTimer = nextFootstep();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public Statue(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 100.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(HUNTING, false);
    }

    private static int nextFootstep() {
        return FOOTSTEP_MIN + (int) (Math.random() * (FOOTSTEP_MAX - FOOTSTEP_MIN));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || !Anims.moving(this)) return;
        if (--footstepTimer > 0) return;
        level().playSound(null, getX(), getY(), getZ(), Sounds.FOOTSTEPS.get(), SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.2F);
        footstepTimer = nextFootstep();
    }

    public boolean isHunting() { return entityData.get(HUNTING); }

    public void setHunting(boolean hunting) {
        entityData.set(HUNTING, hunting);
        if (!hunting) {
            setTarget(null);
            getNavigation().stop();
        }
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, false));
        targetSelector.addGoal(1, Goals.huntPlayers(this));
    }
    
    @Override
    public void setTarget(LivingEntity target) {
        super.setTarget(isHunting() ? target : null);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Hunting", isHunting());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(HUNTING, tag.getBoolean("Hunting"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(Anims.walking(this, 5));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
