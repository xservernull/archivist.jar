// defines tenature, a harmless player lookalike that reacts when watched for too long.

package com.xservernull.archivist.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class Tenature extends PathfinderMob {
    private static final EntityDataAccessor<Optional<UUID>> SKIN_TARGET =
            SynchedEntityData.defineId(Tenature.class, EntityDataSerializers.OPTIONAL_UUID);

    private int stareTicks;
    private int disappointedTicks;

    public Tenature(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, Goals.watch(this, 16.0F));
        goalSelector.addGoal(2, Goals.wander(this, 0.3));
        goalSelector.addGoal(3, Goals.lookAround(this));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKIN_TARGET, Optional.empty());
    }
    
    public Optional<UUID> skinTarget() { return entityData.get(SKIN_TARGET); }

    public void setSkinTarget(UUID player) { entityData.set(SKIN_TARGET, Optional.ofNullable(player)); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        Player nearest = level().getNearestPlayer(this, 32.0);
        if (nearest == null) return;
        if (isLookedAtBy(nearest)) {
            if (++stareTicks > 100) {
                lookAt(nearest, 180.0F, 180.0F);
                if (++disappointedTicks > 60) {
                    Vec3 away = position().subtract(nearest.position()).normalize().scale(3);
                    getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 0.25);
                }
            }
        } else {
            stareTicks = Math.max(0, stareTicks - 1);
            disappointedTicks = Math.max(0, disappointedTicks - 1);
        }
    }

    private boolean isLookedAtBy(Player player) {
        return player.getLookAngle().dot(position().subtract(player.position()).normalize()) > 0.85;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        skinTarget().ifPresent(id -> tag.putUUID("SkinTarget", id));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSkinTarget(tag.hasUUID("SkinTarget") ? tag.getUUID("SkinTarget") : null);
    }
}
