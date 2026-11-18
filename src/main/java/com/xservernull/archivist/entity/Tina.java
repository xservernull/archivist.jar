// defines tina’s hostile behavior and the owner-controlled phases used by the echo chamber event.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

public final class Tina extends Monster implements GeoEntity, Owned {
    public enum Phase { STILL, CHASE }
    
    private static final double CHASE_SPEED = 0.3;
    
    private static final double HOLD_DIST = 1.0;
    
    private static final double REACH = 1.6;
    private static final int ATTACK_INTERVAL = 20;
    
    private static final int OWNER_CHECK = 20;
    private static final int STEP_INTERVAL = 5;
    private static final int WHISPER_INTERVAL = 40;
    private static final double WHISPER_RANGE = 5.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private Phase phase = Phase.STILL;
    private int attackCooldown;
    private int stunTicks;
    private int stepTicks;
    private int whisperTicks = WHISPER_INTERVAL;

    public Tina(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder attributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35)
                .add(Attributes.FOLLOW_RANGE, 40.0) // 32 + the old +8 modifier
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0) // was 6; low so players can survive a few hits while running
                .add(Attributes.ATTACK_KNOCKBACK, 0.0);
    }

    // ---- driven by the event class ----
    
    public void setOwner(UUID id) {
        owner = id;
        phase = Phase.STILL;
        lockForOwner();
    }

    @Override
    public Optional<UUID> owner() { return Optional.ofNullable(owner); }

    public Phase phase() { return phase; }
    
    public void startChase() {
        if (owner != null) phase = Phase.CHASE;
    }
    
    public void stun(int ticks) {
        stunTicks = Math.max(stunTicks, ticks);
    }

    private void lockForOwner() {
        noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
        setPersistenceRequired();
    }

    // ---- behavior ----

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(3, Goals.wander(this, 1.0));
        targetSelector.addGoal(1, Goals.huntPlayers(this));
    }

    @Override
    public void tick() {
        if (owner != null) noPhysics = true; // like a vex: no collision with the stairs, no suffocation
        super.tick();
        if (!level().isClientSide) sounds();
        if (owner == null || !(level() instanceof ServerLevel server)) return;
        if (phase == Phase.STILL && tickCount % OWNER_CHECK != 0) return;
        if (!(server.getPlayerByUUID(owner) instanceof ServerPlayer player) || !player.isAlive()) {
            discard();
            return;
        }
        if (stunTicks > 0) {
            stunTicks--;
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (phase == Phase.CHASE) chase(player);
    }

    private void sounds() {
        if (getDeltaMovement().horizontalDistanceSqr() > 0.001) {
            if (++stepTicks >= STEP_INTERVAL) {
                stepTicks = 0;
                playSound(Sounds.TINASTEP.get(), 0.5F, 1.0F + random.nextFloat() * 0.2F);
            }
        } else {
            stepTicks = 0;
        }
        boolean hunting = phase == Phase.CHASE || getTarget() != null;
        if (hunting && level().getNearestPlayer(this, WHISPER_RANGE) != null) {
            if (++whisperTicks >= WHISPER_INTERVAL) {
                whisperTicks = 0;
                playSound(Sounds.TINAAMBIENT.get(), 0.06F, 1.0F);
            }
        } else {
            whisperTicks = WHISPER_INTERVAL;
        }
    }

    @Override
    protected SoundEvent getAmbientSound() { return null; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return Sounds.BYETINA.get(); }

    @Override
    protected SoundEvent getDeathSound() { return Sounds.BYETINA.get(); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}
    
    private void chase(ServerPlayer player) {
        Vec3 to = player.position().subtract(position());
        double distance = to.length();
        faceTowards(to);
        if (distance > HOLD_DIST) {
            Vec3 step = to.scale(Math.min(CHASE_SPEED, distance - HOLD_DIST) / distance);
            setPos(getX() + step.x, getY() + step.y, getZ() + step.z);
            setDeltaMovement(step); // synced to the client, which is what starts the walk animation
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        if (attackCooldown > 0) {
            attackCooldown--;
        } else if (distance <= REACH) {
            doHurtTarget(player);
            attackCooldown = ATTACK_INTERVAL;
        }
    }

    private void faceTowards(Vec3 direction) {
        if (direction.x * direction.x + direction.z * direction.z < 0.0001) return;
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * (180.0 / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }
    
    @Override
    public void travel(Vec3 travelVector) {
        if (!isNoAi()) super.travel(travelVector);
    }

    // ---- save data ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("OwnerUUID", owner);
        tag.putString("Phase", phase.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.STILL;
        }
        if (owner != null) lockForOwner();
    }

    // ---- animation ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(Anims.locomotion(this, 3));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
