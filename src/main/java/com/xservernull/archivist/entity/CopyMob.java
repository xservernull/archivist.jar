// defines the player-copying mobs, including their skins, names, combat behavior, and sounds.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public final class CopyMob extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> TARGET =
            SynchedEntityData.defineId(CopyMob.class, EntityDataSerializers.STRING);

    private static final float SPEED_START = 0.1F;
    private static final float SPEED_MAX = 0.55F;
    private static final int RAMP_TICKS = 3600;
    private static final int BREATHE_EVERY = 200;
    private static final double BREATHE_RANGE = 12.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final boolean hostile;
    private int aliveTicks;
    private int breatheTicks;

    private CopyMob(EntityType<? extends PathfinderMob> type, Level level, boolean hostile) {
        super(type, level);
        this.hostile = hostile;
        // Not in registerGoals(): Mob's constructor calls that before the field above is set.
        if (!level.isClientSide) addGoals();
    }
    
    public static CopyMob hostile(EntityType<? extends PathfinderMob> type, Level level) {
        return new CopyMob(type, level, true);
    }
    
    public static CopyMob friendly(EntityType<? extends PathfinderMob> type, Level level) {
        return new CopyMob(type, level, false);
    }

    public static AttributeSupplier.Builder hostileAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, SPEED_START)
                .add(Attributes.FOLLOW_RANGE, 128.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.5);
    }

    public static AttributeSupplier.Builder friendlyAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    private void addGoals() {
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(3, Goals.wander(this, 1.0));
        goalSelector.addGoal(2, new CopyFleeGoal(this));
        goalSelector.addGoal(3, Goals.watch(this, hostile ? 8.0F : 16.0F));
        goalSelector.addGoal(4, Goals.lookAround(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, hostile ? Goals.huntPlayers(this)
                : new NearestAttackableTargetGoal<>(this, Monster.class, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(TARGET, "");
    }
    
    public UUID targetPlayer() {
        String raw = entityData.get(TARGET);
        if (raw.isEmpty()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTargetPlayer(Player player) {
        entityData.set(TARGET, player.getUUID().toString());
    }

    @Override
    public Component getDisplayName() {
        UUID id = targetPlayer();
        Player player = id == null ? null : level().getPlayerByUUID(id);
        return player != null ? player.getDisplayName() : super.getDisplayName();
    }

    @Override
    public boolean shouldShowName() { return true; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (++breatheTicks >= BREATHE_EVERY) {
            breatheTicks = 0;
            if (level().getNearestPlayer(this, BREATHE_RANGE) != null) {
                level().playSound(null, getX(), getY(), getZ(), Sounds.BREATHING.get(), SoundSource.HOSTILE, 0.5F, 1.0F);
            }
        }
        if (hostile && aliveTicks < RAMP_TICKS) {
            aliveTicks++;
            applyRampedSpeed();
        }
    }

    private void applyRampedSpeed() {
        float t = Math.min(1.0F, (float) aliveTicks / RAMP_TICKS);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SPEED_START + (SPEED_MAX - SPEED_START) * t);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) {
            ItemEntity drop = new ItemEntity(level(), getX(), getY() + 0.5, getZ(), new ItemStack(Items.BOOK));
            drop.setDefaultPickUpDelay();
            level().addFreshEntity(drop);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("TargetUUID", entityData.get(TARGET));
        tag.putInt("AliveTicks", aliveTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(TARGET, tag.getString("TargetUUID"));
        aliveTicks = tag.getInt("AliveTicks");
        if (hostile && aliveTicks > 0) applyRampedSpeed();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(Anims.locomotion(this, hostile ? 3 : 0));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
