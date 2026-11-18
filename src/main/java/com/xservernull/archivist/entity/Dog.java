// defines the invulnerable dog companion, including taming, sitting, following, and its childhood home interaction.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.events.ChildhoodHome;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.Set;

public final class Dog extends Wolf {
    private static final int FOLLOW_DISTANCE = 4;
    private static final int TELEPORT_DISTANCE = 12;
    private static final int REPATH_TICKS = 10;
    
    private boolean following;
    private int repathTimer;

    public Dog(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setCustomName(Component.literal("dog"));
        setOrderedToSit(true);
        setInSittingPose(true);
        setPersistenceRequired();
    }
    
    public void befriend(ServerPlayer player) {
        setTame(true, true);
        setOwnerUUID(player.getUUID());
        following = true;
        super.setOrderedToSit(false);
        setInSittingPose(false);
        getNavigation().stop();
    }
    
    private void toggleFollow(ServerPlayer player) {
        if (following && isOwnedBy(player)) {
            following = false;
            setOrderedToSit(true);
            setInSittingPose(true);
            getNavigation().stop();
        } else {
            befriend(player);
        }
    }

    public boolean isFollowing() {
        return following;
    }

    @Override
    public void setOrderedToSit(boolean sit) {
        if (following && sit) return;
        super.setOrderedToSit(sit);
    }

    // ---- immortal: setInvulnerable alone does not stop the void, /kill or damage that bypasses invulnerability

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void kill() {
    }
    
    private void rescue() {
        Player near = level().getNearestPlayer(this, -1.0);
        double x, y, z;
        if (near != null) {
            x = near.getX();
            y = near.getY();
            z = near.getZ();
        } else {
            net.minecraft.core.BlockPos spawn = level().getSharedSpawnPos();
            x = spawn.getX() + 0.5;
            y = spawn.getY();
            z = spawn.getZ() + 0.5;
        }
        getNavigation().stop();
        setDeltaMovement(0.0, 0.0, 0.0);
        fallDistance = 0.0F;
        teleportTo(x, y, z);
    }
    
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || !following;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (getY() < level().getMinBuildHeight() - 8) rescue();
        if (!following) {
            if (!isOrderedToSit() || !isInSittingPose()) {
                super.setOrderedToSit(true);
                setInSittingPose(true);
            }
            getNavigation().stop();
            setDeltaMovement(0.0, getDeltaMovement().y, 0.0);
            return;
        }
        if (isOrderedToSit()) super.setOrderedToSit(false);
        if (isInSittingPose()) setInSittingPose(false);
        LivingEntity owner = getOwner();
        if (owner == null) {
            // the owner is not in this dimension: follow them across (or wait while they are offline)
            if (tickCount % 20 == 0 && level() instanceof ServerLevel here && getOwnerUUID() != null) {
                ServerPlayer other = here.getServer().getPlayerList().getPlayer(getOwnerUUID());
                if (other != null && other.level() != here) {
                    teleportTo(other.serverLevel(), other.getX(), other.getY(), other.getZ(),
                            Set.of(), getYRot(), getXRot());
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
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Following", following);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        following = tag.getBoolean("Following");
    }

    public static AttributeSupplier.Builder attributes() {
        return Wolf.createAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.35);
    }
    
    public static void followAcross(ServerPlayer player, ResourceKey<Level> from) {
        MinecraftServer server = player.getServer();
        ServerLevel old = server == null ? null : server.getLevel(from);
        if (old == null || old == player.serverLevel()) return;
        for (Dog dog : old.getEntities(EntityTypeTest.forClass(Dog.class),
                d -> d.isFollowing() && player.getUUID().equals(d.getOwnerUUID()))) {
            dog.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                    Set.of(), dog.getYRot(), dog.getXRot());
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer owner) {
            if (ChildhoodHome.onDogUsed(owner, this)) return InteractionResult.SUCCESS;
            toggleFollow(owner);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}