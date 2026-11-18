// defines collectible drawings in the mansion that heal the owner and temporarily stun their tina entities.

package com.xservernull.archivist.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.Optional;
import java.util.UUID;

public final class MansionDrawing extends Entity implements Owned {
    public static final int FACE_FLOOR = 0;
    public static final int FACE_CEILING = 1;
    public static final int FACE_NORTH = 2;
    public static final int FACE_SOUTH = 3;
    public static final int FACE_EAST = 4;
    public static final int FACE_WEST = 5;

    private static final int STUN_TICKS = 40;

    private static final EntityDataAccessor<Integer> KIND =
            SynchedEntityData.defineId(MansionDrawing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACING =
            SynchedEntityData.defineId(MansionDrawing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(MansionDrawing.class, EntityDataSerializers.OPTIONAL_UUID);

    public MansionDrawing(EntityType<? extends MansionDrawing> type, Level level) {
        super(type, level);
        noPhysics = true;
        setGlowingTag(true);
    }
    
    public static MansionDrawing create(Level level, double x, double y, double z, int kind, int facing, UUID owner) {
        MansionDrawing drawing = new MansionDrawing(Mobs.MANSION_DRAWING.get(), level);
        drawing.setPos(x, y, z);
        drawing.entityData.set(KIND, kind);
        drawing.entityData.set(FACING, facing);
        drawing.entityData.set(OWNER, Optional.of(owner));
        return drawing;
    }

    public int kind() { return entityData.get(KIND); }

    public int facing() { return entityData.get(FACING); }

    @Override
    public Optional<UUID> owner() { return entityData.get(OWNER); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, 0);
        builder.define(FACING, 0);
        builder.define(OWNER, Optional.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 4 == 0) {
            level().addParticle(ParticleTypes.END_ROD,
                    getX() + (random.nextDouble() - 0.5) * 0.4,
                    getY() + (random.nextDouble() - 0.5) * 0.4,
                    getZ() + (random.nextDouble() - 0.5) * 0.4,
                    0, 0.01, 0);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer picker) || !isOwnedBy(picker.getUUID())) {
            return InteractionResult.PASS;
        }
        picker.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        ServerLevel server = (ServerLevel) level();
        server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 20, 0.2, 0.2, 0.2, 0.05);
        server.playSound(null, blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.AMBIENT, 0.5F, 1.1F);
        for (Tina tina : server.getEntities(EntityTypeTest.forClass(Tina.class), t -> t.isOwnedBy(picker.getUUID()))) {
            tina.stun(STUN_TICKS);
        }
        discard();
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("Kind"));
        entityData.set(FACING, tag.getInt("Facing"));
        entityData.set(OWNER, tag.hasUUID("Owner") ? Optional.of(tag.getUUID("Owner")) : Optional.empty());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Kind", kind());
        tag.putInt("Facing", facing());
        owner().ifPresent(id -> tag.putUUID("Owner", id));
    }

    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isInvulnerable() { return true; }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096; }
}
