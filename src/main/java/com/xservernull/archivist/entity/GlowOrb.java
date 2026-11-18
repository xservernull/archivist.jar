// defines collectible glow orbs that belong to a player and report collection to the neverforgetyou event.

package com.xservernull.archivist.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

public final class GlowOrb extends Entity implements Owned {
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(GlowOrb.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> INDEX =
            SynchedEntityData.defineId(GlowOrb.class, EntityDataSerializers.INT);
    
    public static final int MESSAGE_BASE = 100;
    public static final double COLLECT_RANGE = 1.2;
    private static final double PULL_RANGE = 20.0;
    private static final double PULL_SPEED = 0.003;
    private static final double HOMING_RANGE = 200.0;
    private static final double HOMING_SPEED = 0.012;
    
    @FunctionalInterface
    public interface Collector {
        void collected(ServerPlayer player, GlowOrb orb);
    }

    private static Collector collector;
    
    public static void setCollector(Collector value) { collector = value; }

    private boolean collected;
    private boolean homing;
    private boolean active;
    private boolean anchored;
    private double baseX, baseY, baseZ;
    private int orbTick;

    public GlowOrb(EntityType<? extends GlowOrb> type, Level level) {
        super(type, level);
        noPhysics = true;
        setGlowingTag(true);
    }

    public static GlowOrb create(Level level, Vec3 pos, int index, UUID owner) {
        GlowOrb orb = new GlowOrb(Mobs.GLOW_ORB.get(), level);
        orb.setPos(pos);
        orb.entityData.set(INDEX, index);
        orb.entityData.set(OWNER, Optional.of(owner));
        orb.active = index >= MESSAGE_BASE;
        return orb;
    }

    @Override
    public Optional<UUID> owner() { return entityData.get(OWNER); }

    public int index() { return entityData.get(INDEX); }

    public boolean isActive() { return active; }

    public void setActive(boolean value) { active = value; }

    public boolean isHoming() { return homing; }

    public void setHoming(boolean value) { homing = value; }

    public boolean isCollected() { return collected; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(INDEX, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || collected) return;
        if (!anchored) {
            baseX = getX();
            baseY = getY();
            baseZ = getZ();
            anchored = true;
        }
        orbTick++;
        Player owner = owner().map(id -> level().getPlayerByUUID(id)).orElse(null);
        if (owner != null) {
            double distance = distanceTo(owner);
            double range = homing ? HOMING_RANGE : PULL_RANGE;
            double speed = homing ? HOMING_SPEED : PULL_SPEED;
            if (distance > COLLECT_RANGE && distance < range) {
                Vec3 step = owner.position().subtract(position()).normalize().scale(speed);
                baseX += step.x;
                baseZ += step.z;
            }
            // measured to the player's body box, not their feet: the orb floats about 2.5 blocks above the ground
            boolean touching = owner.getBoundingBox().distanceToSqr(position()) < COLLECT_RANGE * COLLECT_RANGE;
            if (active && touching && owner instanceof ServerPlayer player) {
                collected = true;
                if (collector != null) collector.collected(player, this);
                discard();
                return;
            }
        }
        setPos(baseX, baseY + Math.sin(orbTick * 0.05) * 0.25, baseZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(INDEX, tag.getInt("Index"));
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
        collected = tag.getBoolean("Collected");
        homing = tag.getBoolean("Homing");
        active = tag.getBoolean("Active");
        orbTick = tag.getInt("OrbTick");
        anchored = tag.contains("BaseX");
        if (anchored) {
            baseX = tag.getDouble("BaseX");
            baseY = tag.getDouble("BaseY");
            baseZ = tag.getDouble("BaseZ");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Index", index());
        owner().ifPresent(id -> tag.putUUID("Owner", id));
        tag.putBoolean("Collected", collected);
        tag.putBoolean("Homing", homing);
        tag.putBoolean("Active", active);
        tag.putInt("OrbTick", orbTick);
        if (anchored) {
            tag.putDouble("BaseX", baseX);
            tag.putDouble("BaseY", baseY);
            tag.putDouble("BaseZ", baseZ);
        }
    }
    
    @Override
    public boolean broadcastToPlayer(ServerPlayer viewer) {
        return viewer.isSpectator() || owner().map(viewer.getUUID()::equals).orElse(true);
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isInvulnerable() { return true; }
}
