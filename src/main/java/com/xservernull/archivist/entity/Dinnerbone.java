// defines dinnerbone, a large horse-like monster with custom attacks, sounds, and a home dimension.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Set;

public final class Dinnerbone extends Monster implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    
    private ResourceKey<Level> home;

    public Dinnerbone(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }
    
    public void setHomeDimension(ResourceKey<Level> dimension) {
        this.home = dimension;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (home == null || !(level() instanceof ServerLevel current) || current.dimension().equals(home)) return;
        ServerLevel target = current.getServer().getLevel(home);
        if (target == null) discard();
        else teleportTo(target, getX(), getY(), getZ(), Set.of(), getYRot(), getXRot());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.putString("HomeDimension", home.location().toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation id = tag.contains("HomeDimension") ? ResourceLocation.tryParse(tag.getString("HomeDimension")) : null;
        home = id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.55)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.JUMP_STRENGTH, 0.62);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DinnerboneWindupGoal(this, 1.0));
        goalSelector.addGoal(3, Goals.wander(this, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, Goals.huntPlayers(this));
    }
    
    void startWindup() {
        triggerAnim("attack", "attack");
        level().playSound(null, this, Sounds.DINNERBONE_NEIGH.get(), SoundSource.HOSTILE, 1.4F, 0.85F);
    }

    @Override
    protected SoundEvent getAmbientSound() { return Sounds.DINNERBONE_AMBIENT.get(); }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return Sounds.DINNERBONE_HURT.get(); }

    @Override
    protected SoundEvent getDeathSound() { return Sounds.DINNERBONE_DEATH.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(Sounds.DINNERBONE_HOOFSTEP.get(), 0.5F, 0.8F + random.nextFloat() * 0.2F);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) { return false; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(Anims.locomotion(this, 5));
        controllers.add(new AnimationController<>(this, "attack", 1, state -> PlayState.STOP)
                .triggerableAnim("attack", Anims.once(this, "attack")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
