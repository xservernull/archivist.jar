// defines them, a persistent hostile entity with long-range detection, combat goals, and weapon behavior.

package com.xservernull.archivist.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;

public class Them extends PathfinderMob {
    private static final int WARMUP = 50;

    private static final int SEEK_SCAN_INTERVAL = 20, SEEK_TIMEOUT = 200;
    private static final double SEARCH_RADIUS = 24.0;
    
    private int warmupTicks = WARMUP;
    
    private boolean seekingWeapon;
    private int seekTimer, seekTimeout;

    public Them(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 160.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.ARMOR, 2.0)
                .add(Attributes.ATTACK_KNOCKBACK, 0.0)
                .add(Attributes.STEP_HEIGHT, 1.1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new ThemClimb(this));
        goalSelector.addGoal(2, new ThemPray(this));
        goalSelector.addGoal(3, new ThemAlert(this));
        goalSelector.addGoal(4, new ThemChase(this));
        goalSelector.addGoal(8, Goals.watch(this, 20.0F));
        targetSelector.addGoal(1, Goals.huntPlayers(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (warmupTicks > 0) {
            warmupTicks--;
            if (getTarget() != null) setTarget(null);
        }
        if (getTarget() != null && !isSprinting()) setSprinting(true);
        if (seekingWeapon && !isDeadOrDying()) tickWeaponSearch();
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (warmupTicks > 0) return false;
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            Vec3 push = position().subtract(living.position()).normalize().scale(-0.15);
            living.setDeltaMovement(living.getDeltaMovement().add(push.x, 0.1, push.z));
        }
        return hit;
    }

    // ---- weapons: picks up a sword or an axe when its hand is empty, and always drops it ----

    @Override
    public boolean canPickUpLoot() { return getMainHandItem().isEmpty(); }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
    
    public void seekWeapon() {
        seekingWeapon = true;
        seekTimer = 0;
        seekTimeout = 0;
    }
    
    private void tickWeaponSearch() {
        if (!getMainHandItem().isEmpty() || ++seekTimeout >= SEEK_TIMEOUT) {
            seekingWeapon = false;
            return;
        }
        if (++seekTimer < SEEK_SCAN_INTERVAL) return;
        seekTimer = 0;
        AABB box = getBoundingBox().inflate(SEARCH_RADIUS);
        level().getEntitiesOfClass(ItemEntity.class, box, item -> item.isAlive() && wantsToPickUp(item.getItem()))
                .stream()
                .min(Comparator.comparingDouble(item -> item.distanceToSqr(this)))
                .ifPresent(item -> getNavigation().moveTo(item, 1.0));
    }

    @Override
    protected float getEquipmentDropChance(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? 2.0F : super.getEquipmentDropChance(slot);
    }

    // ---- save: the held item is saved by Mob itself; only the warmup is ours ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("WarmupTicks", warmupTicks);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        warmupTicks = tag.getInt("WarmupTicks");
    }

    // ---- silence ----

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return null; }

    @Override
    protected SoundEvent getDeathSound() { return null; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundType sound = state.getSoundType();
        playSound(sound.getStepSound(), sound.getVolume() * 0.15F, sound.getPitch());
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    protected boolean canRide(Entity vehicle) { return false; }
}
