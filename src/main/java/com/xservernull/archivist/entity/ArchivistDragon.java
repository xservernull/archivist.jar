// defines the archivist dragon entity, including its idle animation, fade-in, and explosion state.

package com.xservernull.archivist.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

public final class ArchivistDragon extends PathfinderMob implements GeoEntity {
    public static final int RISE_FADE_TICKS = 30;
    public static final int EXPLOSION_TICKS = 20;
    
    private static final EntityDataAccessor<Integer> EXPLOSION =
            SynchedEntityData.defineId(ArchivistDragon.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ArchivistDragon(EntityType<? extends ArchivistDragon> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setNoAi(true);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2000.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(EXPLOSION, -1);
    }
    
    public float fadeIn(float partialTick) {
        return Math.min(1.0F, (tickCount + partialTick) / RISE_FADE_TICKS);
    }
    
    public float explosionProgress() {
        int ticks = entityData.get(EXPLOSION);
        return ticks < 0 ? -1.0F : Math.min(1.0F, ticks / (float) EXPLOSION_TICKS);
    }
    
    public void explodeAndDiscard() {
        if (!(level() instanceof ServerLevel server)) {
            discard();
            return;
        }
        if (entityData.get(EXPLOSION) >= 0) return;
        entityData.set(EXPLOSION, 0);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 8.0, getZ(), 6, 6.0, 4.0, 6.0, 0.0);
        server.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 4.0F, 0.6F);
        server.playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 4.0F, 0.5F);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        int ticks = entityData.get(EXPLOSION);
        if (ticks < 0) return;
        if (ticks + 1 >= EXPLOSION_TICKS) discard();
        else entityData.set(EXPLOSION, ticks + 1);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) { return true; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(Anims.idle(this, 0));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}