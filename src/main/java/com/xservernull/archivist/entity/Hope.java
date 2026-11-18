// defines hope, the neverforgetyou guide who leads the player through the orb sequence and book finale.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Ground;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Optional;
import java.util.UUID;

public final class Hope extends PathfinderMob implements GeoEntity, Owned {
    public enum Phase { IDLE, WALKING, WAITING, BOOK, DONE }
    
    @FunctionalInterface
    public interface BookHandler {
        void reached(ServerPlayer player, Hope hope);
    }

    private static BookHandler bookHandler;
    
    public static void setBookHandler(BookHandler value) { bookHandler = value; }

    private static final EntityDataAccessor<Boolean> FLYING =
            SynchedEntityData.defineId(Hope.class, EntityDataSerializers.BOOLEAN);

    private static final int LAST_ORB = 9;
    private static final double ARRIVE_DIST = 2.0;
    private static final double WALK_SPEED = 0.35;
    private static final double BOOK_SPEED = 0.12;
    private static final double BOOK_DIST = 2.5;
    private static final double MIN_RADIUS = 20.0;
    private static final double MAX_RADIUS = 50.0;
    
    private static final double IDLE_OFFSET = 3.0;
    private static final double IDLE_RANGE = 4.0;
    private static final double IDLE_FAR = 12.0;
    
    private static final int BEACON_INTERVAL = 2;
    private static final int BEACON_COUNT = 8;
    private static final double BEACON_HEIGHT = 24.0;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private float floatTick;
    private Vec3 velocity = Vec3.ZERO;
    private UUID owner;
    private Phase phase = Phase.IDLE;
    private int nextOrb;
    private Vec3 waypoint;
    private boolean following;

    public Hope(EntityType<? extends Hope> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setNoAi(true);
        setGlowingTag(true); // the outline shows through walls, so she is easy to spot
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FLYING_SPEED, 0.1)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    // ---- driven by the event class ----

    public void setOwner(UUID id) { owner = id; }

    @Override
    public Optional<UUID> owner() { return Optional.ofNullable(owner); }

    public Phase phase() { return phase; }
    
    public void startGuide() {
        nextOrb = 0;
        walk();
    }
    
    public void orbDone(int index) {
        if (index < LAST_ORB) {
            nextOrb = index + 1;
            walk();
        } else {
            phase = Phase.IDLE;
            waypoint = null;
        }
    }
    
    public void startBookMode() {
        phase = Phase.BOOK;
        waypoint = null;
        setFlying(false);
    }

    private void walk() {
        waypoint = null;
        phase = Phase.WALKING;
    }

    // ---- behavior ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FLYING, false);
    }

    public boolean isFlying() { return entityData.get(FLYING); }

    private void setFlying(boolean value) { entityData.set(FLYING, value); }

    @Override
    public void tick() {
        super.tick();
        floatTick += 0.03F;
        if (level().isClientSide) return;
        setDeltaMovement(0, Math.sin(floatTick) * 0.015, 0);
        if (owner == null || !(level() instanceof ServerLevel server)) return;
        if (!(server.getPlayerByUUID(owner) instanceof ServerPlayer player) || !player.isAlive()) return;
        switch (phase) {
            case WALKING -> tickWalk(player, server);
            case BOOK -> tickBook(player);
            case IDLE -> tickIdle(player);
            default -> { }
        }
        if (phase == Phase.WALKING || phase == Phase.WAITING || phase == Phase.BOOK) beacon(server, player);
        // she always looks at the player, except while flying to an orb point (then along her way)
        faceTowards(phase == Phase.WALKING && waypoint != null ? waypoint : player.position());
    }
    
    private void faceTowards(Vec3 target) {
        double dx = target.x - getX();
        double dz = target.z - getZ();
        if (dx * dx + dz * dz < 0.01) return;
        float wanted = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float next = Mth.approachDegrees(getYRot(), wanted, 12.0F);
        setYRot(next);
        yBodyRot = next;
        yHeadRot = next;
    }
    
    private void beacon(ServerLevel server, ServerPlayer player) {
        if (tickCount % BEACON_INTERVAL != 0) return;
        server.sendParticles(player, ParticleTypes.END_ROD, true, getX(), getY() + BEACON_HEIGHT / 2.0, getZ(),
                BEACON_COUNT, 0.15, BEACON_HEIGHT / 2.0, 0.15, 0.0);
    }

    private void tickWalk(ServerPlayer player, ServerLevel server) {
        setFlying(true);
        if (waypoint == null) waypoint = pickWaypoint(player, server);
        if (position().distanceTo(waypoint) <= ARRIVE_DIST) {
            GlowOrb orb = GlowOrb.create(server, position().add(0, 1.0, 0), nextOrb, player.getUUID());
            orb.setActive(true);
            orb.setHoming(nextOrb >= 8); // from 8 collected the last two orbs come to the player (range 200)
            server.addFreshEntity(orb);
            phase = Phase.WAITING;
            setFlying(false);
        } else {
            glide(waypoint, WALK_SPEED);
        }
    }
    
    private void tickIdle(ServerPlayer player) {
        Vec3 target = player.position().add(IDLE_OFFSET, 1.5, 0);
        double distance = position().distanceTo(target);
        // she stays where she is while they talk; only a long way off (a fall, a teleport) makes her follow
        if (!following && distance > IDLE_FAR) following = true;
        else if (following && distance < IDLE_RANGE) following = false;
        setFlying(following);
        if (following) glide(target, Math.min(0.25, 0.05 + distance * 0.03));
    }

    private void tickBook(ServerPlayer player) {
        if (distanceTo(player) > BOOK_DIST) {
            glide(player.position().add(0, 1.5, 0), BOOK_SPEED);
            return;
        }
        phase = Phase.DONE;
        if (bookHandler != null) bookHandler.reached(player, this);
    }
    
    private void glide(Vec3 target, double speed) {
        Vec3 desired = target.subtract(position()).normalize().scale(speed);
        velocity = new Vec3(
                velocity.x * 0.85 + desired.x * 0.15,
                velocity.y * 0.92 + desired.y * 0.08,
                velocity.z * 0.85 + desired.z * 0.15);
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
    }
    
    private Vec3 pickWaypoint(ServerPlayer player, ServerLevel server) {
        double x = 0, z = 0;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double radius = MIN_RADIUS + random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            x = player.getX() + Math.cos(angle) * radius;
            z = player.getZ() + Math.sin(angle) * radius;
            if (Ground.isLoaded(server, (int) Math.floor(x), (int) Math.floor(z))) break;
        }
        return new Vec3(x, Ground.openY(server, (int) Math.floor(x), (int) Math.floor(z), player.getY()) + 1.5, z);
    }

    // ---- save data ----

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("OwnerUUID", owner);
        tag.putString("Phase", phase.name());
        tag.putInt("NextOrb", nextOrb);
        if (waypoint != null) {
            tag.putDouble("WaypointX", waypoint.x);
            tag.putDouble("WaypointY", waypoint.y);
            tag.putDouble("WaypointZ", waypoint.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("OwnerUUID") ? tag.getUUID("OwnerUUID") : null;
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        nextOrb = tag.getInt("NextOrb");
        waypoint = tag.contains("WaypointX")
                ? new Vec3(tag.getDouble("WaypointX"), tag.getDouble("WaypointY"), tag.getDouble("WaypointZ"))
                : null;
    }

    // ---- animation and flags ----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation idle = Anims.loop(this, "idle");
        RawAnimation fly = Anims.loop(this, "new");
        controllers.add(new AnimationController<>(this, "main", 10,
                state -> state.setAndContinue(isFlying() ? fly : idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public boolean isInvulnerableTo(DamageSource source) { return true; }

    @Override
    public boolean isPushable() { return false; }
}