// defines him and the stages of his far lands encounter, including wandering, staring, and chasing the player.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.events.HimEncounter;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.Set;
import java.util.UUID;

public final class Him extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Boolean> SITTING =
            SynchedEntityData.defineId(Him.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TAMED =
            SynchedEntityData.defineId(Him.class, EntityDataSerializers.BOOLEAN);
    
    private static final EntityDataAccessor<Float> STRETCH =
            SynchedEntityData.defineId(Him.class, EntityDataSerializers.FLOAT);
    
    private static final EntityDataAccessor<Boolean> WILD =
            SynchedEntityData.defineId(Him.class, EntityDataSerializers.BOOLEAN);

    private enum Stage { IDLE, WANDER, STANDOFF, CHASE }

    private static final int POPPIES_TO_TAME = 5;
    private static final double WANDER_SPEED = 0.045, FLY_SPEED = 0.22;
    private static final double STANDOFF_DISTANCE = 3.0, CATCH_DISTANCE = 1.5, PUSHBACK = 8.0;
    private static final int STANDOFF_TICKS = 300, HEARTBEAT_TICKS = 200, DIE_TICKS = 200;
    private static final int MIN_CHASE_TICKS = 30, HIT_COOLDOWN = 12;
    private static final int INTRO_STRETCH = 100, RETALIATION_STRETCH = 60;
    private static final double TELEPORT_MIN = 20.0, TELEPORT_MAX = 40.0, SEARCH_RADIUS = 256.0;
    private static final int NEAR_SOUND_TICKS = 40;
    private static final double JITTER_MIN = 0.15, JITTER_MAX = 3.0, JITTER_SIDE = 0.35;
    private static final int FOLLOW_DISTANCE = 4, TELEPORT_DISTANCE = 12, REPATH_TICKS = 10;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Stage stage = Stage.IDLE;
    
    private UUID encounterId;
    
    private UUID huntedId;
    private UUID ownerId;
    private boolean following;
    private int poppies;

    private int stretchTotal, stretchTicks;
    private int standoffTicks, chaseTicks, hitCooldown, nearTimer, repathTimer, searchTimer;
    private LivingEntity chaseTarget;
    private Player cachedTrack;
    private double jitter = 1.0;
    private int jitterHold;

    public Him(EntityType<? extends Him> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder attributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 30.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SITTING, false);
        builder.define(TAMED, false);
        builder.define(STRETCH, -1.0F);
        builder.define(WILD, false);
    }

    public boolean isSitting() { return entityData.get(SITTING); }

    public boolean isTamed() { return entityData.get(TAMED); }
    
    public boolean isWild() { return entityData.get(WILD); }
    
    public float getStretchProgress() { return entityData.get(STRETCH); }
    
    public UUID encounterId() { return encounterId; }

    public boolean isChasing() { return stage == Stage.CHASE; }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, Goals.wander(this, 0.6, () -> stage == Stage.IDLE && !isSitting() && !following));
        goalSelector.addGoal(2, Goals.watch(this, 8.0F));
    }
    
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || (!isTamed() && stage != Stage.IDLE);
    }

    // ---- starting ----
    
    public void beginEncounter(ServerPlayer player) {
        encounterId = player.getUUID();
        stage = Stage.WANDER;
        setNoGravity(true);
        startStretch(INTRO_STRETCH);
    }

    private void startStretch(int ticks) {
        stretchTotal = ticks;
        stretchTicks = 0;
        entityData.set(STRETCH, 0.0F);
    }

    private void endStretch() {
        stretchTotal = 0;
        stretchTicks = 0;
        entityData.set(STRETCH, -1.0F);
    }

    // ---- tick ----

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        boolean wild = !isTamed() && stage != Stage.IDLE;
        if (entityData.get(WILD) != wild) entityData.set(WILD, wild);
        if (stretchTotal > 0 && ++stretchTicks >= stretchTotal) endStretch();
        else if (stretchTotal > 0) entityData.set(STRETCH, Math.min(1.0F, (float) stretchTicks / stretchTotal));

        switch (stage) {
            case CHASE -> tickChase();
            case STANDOFF -> tickStandoff();
            case WANDER -> {
                if (stretchTotal == 0) tickWander();
            }
            case IDLE -> {
                if (isTamed() && following) tickFollow();
            }
        }
        if (!isTamed() && stage != Stage.CHASE && ++nearTimer >= NEAR_SOUND_TICKS) {
            nearTimer = 0;
            level().playSound(null, blockPosition(), Sounds.NEAR.get(), SoundSource.HOSTILE, 0.8F, 1.0F);
        }
    }

    private double nextJitter() {
        if (--jitterHold <= 0) {
            jitter = JITTER_MIN + random.nextDouble() * (JITTER_MAX - JITTER_MIN);
            jitterHold = 4 + random.nextInt(11);
        }
        return jitter;
    }
    
    private void floatToward(Vec3 to, double speed) {
        Vec3 delta = to.subtract(position());
        double dist = delta.length();
        if (dist < 1.0E-4) return;
        double len = Math.min(speed * nextJitter(), dist);
        Vec3 dir = delta.scale(1.0 / dist);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 step = dir.scale(len).add(side.scale((random.nextDouble() * 2.0 - 1.0) * len * JITTER_SIDE));
        Vec3 next = position().add(step);
        setPos(next.x, next.y, next.z);
        setDeltaMovement(step);
        lookAt(EntityAnchorArgument.Anchor.EYES, to);
    }

    private Vec3 aimPoint(LivingEntity entity) {
        return entity.position().add(0, entity.getEyeHeight() * 0.5, 0);
    }

    // ---- WANDER ----

    private void tickWander() {
        Player target = nearestTrackable();
        if (target == null) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        Vec3 to = aimPoint(target);
        if (position().distanceTo(to) <= STANDOFF_DISTANCE) {
            stage = Stage.STANDOFF;
            standoffTicks = 0;
            huntedId = target.getUUID();
            setDeltaMovement(Vec3.ZERO);
            lookAt(EntityAnchorArgument.Anchor.EYES, to);
            level().playSound(null, target.blockPosition(), Sounds.HEARTBEAT.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
            return;
        }
        floatToward(to, WANDER_SPEED);
    }

    private Player nearestTrackable() {
        if (searchTimer-- > 0 && cachedTrack != null && trackable(cachedTrack)) return cachedTrack;
        searchTimer = 10;
        cachedTrack = level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(SEARCH_RADIUS), Him::trackable)
                .stream().min(Comparator.comparingDouble(p -> p.distanceToSqr(this))).orElse(null);
        return cachedTrack;
    }

    private static boolean trackable(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative();
    }

    // ---- STANDOFF ----

    private void tickStandoff() {
        Player hunted = huntedId == null ? null : level().getPlayerByUUID(huntedId);
        if (hunted == null || !trackable(hunted)) {
            endStandoff();
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        lookAt(EntityAnchorArgument.Anchor.EYES, aimPoint(hunted));
        standoffTicks++;
        if (hunted instanceof ServerPlayer player) {
            if (standoffTicks % 20 == 1) HimEncounter.darkness(player, true);
            if (standoffTicks % HEARTBEAT_TICKS == 0) {
                level().playSound(null, player.blockPosition(), Sounds.HEARTBEAT.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
            }
        }
        if (standoffTicks >= STANDOFF_TICKS) {
            endStandoff();
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = TELEPORT_MIN + random.nextDouble() * (TELEPORT_MAX - TELEPORT_MIN);
            setPos(getX() + Math.cos(angle) * dist, getY(), getZ() + Math.sin(angle) * dist);
        }
    }

    private void endStandoff() {
        stage = Stage.WANDER;
        clearDarkness();
    }

    private void clearDarkness() {
        if (huntedId != null && level().getPlayerByUUID(huntedId) instanceof ServerPlayer player) {
            HimEncounter.darkness(player, false);
        }
        huntedId = null;
    }

    // ---- CHASE (always a retaliation) ----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker) beginRetaliation(attacker);
        return hurt;
    }

    private void beginRetaliation(LivingEntity attacker) {
        if (!attacker.isAlive() || attacker.isInvulnerable() || attacker.level() != level()) return;
        if (attacker instanceof Player player && (player.isCreative() || player.isSpectator())) return;
        if (stage == Stage.CHASE && chaseTarget == attacker) return;
        clearDarkness();
        stage = Stage.CHASE;
        chaseTarget = attacker;
        chaseTicks = 0;
        hitCooldown = 0;
        following = false;
        entityData.set(SITTING, false);
        setNoGravity(true);
        setTarget(attacker);
        getNavigation().stop();
        Vec3 away = position().subtract(attacker.position());
        if (away.lengthSqr() > 1.0E-4) {
            Vec3 pushed = position().add(away.normalize().scale(PUSHBACK));
            setPos(pushed.x, pushed.y, pushed.z);
        }
        startStretch(RETALIATION_STRETCH);
        level().playSound(null, attacker.blockPosition(), Sounds.DIE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    private void tickChase() {
        LivingEntity target = chaseTarget;
        if (target == null || !target.isAlive() || target.level() != level()) {
            settle();
            return;
        }
        chaseTicks++;
        if (hitCooldown > 0) hitCooldown--;
        if (chaseTicks % DIE_TICKS == 0) {
            level().playSound(null, target.blockPosition(), Sounds.DIE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        Vec3 to = aimPoint(target);
        if (position().distanceTo(to) <= CATCH_DISTANCE && chaseTicks >= MIN_CHASE_TICKS && hitCooldown == 0) {
            level().playSound(null, target.blockPosition(), Sounds.DIE.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
            target.hurt(damageSources().mobAttack(this), Float.MAX_VALUE);
            hitCooldown = HIT_COOLDOWN;
        }
        floatToward(to, FLY_SPEED);
    }
    
    private void settle() {
        stage = Stage.IDLE;
        chaseTarget = null;
        endStretch();
        setTarget(null);
        setNoGravity(false);
        encounterId = null;
        entityData.set(TAMED, true);
    }

    // ---- taming and following ----

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isTamed() && held.is(Items.POPPY)) {
            if (!level().isClientSide) {
                if (!player.getAbilities().instabuild) held.shrink(1);
                poppies++;
                level().playSound(null, blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 1.0F,
                        0.9F + random.nextFloat() * 0.2F);
                if (poppies >= POPPIES_TO_TAME && player instanceof ServerPlayer tamer) tame(tamer);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (isTamed() && hand == InteractionHand.MAIN_HAND && held.isEmpty() && stage == Stage.IDLE) {
            if (!level().isClientSide) {
                if (isSitting()) {
                    entityData.set(SITTING, false);
                    following = true;
                    ownerId = player.getUUID();
                } else {
                    entityData.set(SITTING, true);
                    following = false;
                    getNavigation().stop();
                }
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private void tame(ServerPlayer tamer) {
        clearDarkness();
        entityData.set(TAMED, true);
        stage = Stage.IDLE;
        chaseTarget = null;
        endStretch();
        setTarget(null);
        setNoGravity(false);
        encounterId = null;
        ownerId = tamer.getUUID();
        following = true;
        HimEncounter.onTamed(tamer);
    }

    private void tickFollow() {
        if (isSitting() || ownerId == null) return;
        Player owner = level().getPlayerByUUID(ownerId);
        if (owner == null) {
            // not in this dimension: follow across (or wait while offline)
            if (tickCount % 20 == 0 && level() instanceof ServerLevel here) {
                ServerPlayer other = here.getServer().getPlayerList().getPlayer(ownerId);
                if (other != null && other.level() != here) {
                    teleportTo(other.serverLevel(), other.getX(), other.getY(), other.getZ(), Set.of(), getYRot(), getXRot());
                }
            }
            return;
        }
        double dist = distanceTo(owner);
        if (dist > TELEPORT_DISTANCE || getY() < level().getMinBuildHeight()) {
            getNavigation().stop();
            teleportTo(owner.getX(), owner.getY(), owner.getZ());
        } else if (dist > FOLLOW_DISTANCE) {
            if (++repathTimer >= REPATH_TICKS || getNavigation().isDone()) {
                repathTimer = 0;
                getNavigation().moveTo(owner, 1.2);
            }
        } else {
            repathTimer = 0;
            getNavigation().stop();
        }
    }
    
    public static void followAcross(ServerPlayer player, ResourceKey<Level> from) {
        MinecraftServer server = player.getServer();
        ServerLevel old = server == null ? null : server.getLevel(from);
        if (old == null || old == player.serverLevel()) return;
        for (Him him : old.getEntities(EntityTypeTest.forClass(Him.class),
                h -> h.isTamed() && h.following && player.getUUID().equals(h.ownerId))) {
            him.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), Set.of(), him.getYRot(), him.getXRot());
        }
    }

    // ---- life cycle ----
    
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && reason.shouldDestroy() && !isTamed() && encounterId != null) {
            HimEncounter.onGone(this, encounterId);
        }
        super.remove(reason);
    }

    // ---- silence once tame ----

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (!isTamed()) super.playStepSound(pos, state);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) { return false; }

    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }

    // ---- save ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Sitting", isSitting());
        tag.putBoolean("HimTamed", isTamed());
        tag.putInt("Poppies", poppies);
        tag.putBoolean("Following", following);
        tag.putInt("Stage", stage == Stage.IDLE ? 0 : 1);
        if (encounterId != null) tag.putUUID("Encounter", encounterId);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(SITTING, tag.getBoolean("Sitting"));
        entityData.set(TAMED, tag.getBoolean("HimTamed"));
        poppies = tag.getInt("Poppies");
        following = tag.getBoolean("Following");
        encounterId = tag.hasUUID("Encounter") ? tag.getUUID("Encounter") : null;
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        // a chase or staring contest does not survive a reload: a hunting Him just wanders on
        stage = tag.getInt("Stage") != 0 && !isTamed() ? Stage.WANDER : Stage.IDLE;
        setNoGravity(stage != Stage.IDLE);
    }

    // ---- animation ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = Anims.loop(this, "idle");
        RawAnimation walk = Anims.loop(this, "walk");
        RawAnimation sit = Anims.loop(this, "sit");
        controllers.add(new AnimationController<>(this, "locomotion", 5,
                state -> state.setAndContinue(isSitting() ? sit : Anims.moving(this) ? walk : idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}