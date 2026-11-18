// defines herobrine as a stronger, slower them variant that carries a diamond sword and teleports near its target.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Ground;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.OptionalInt;

public final class Herobrine extends Them {
    private static final int TELEPORT_INTERVAL = 200;
    private static final double TELEPORT_RANGE = 24.0;
    private static final double TELEPORT_MIN_DIST = 8.0;

    private int teleportCooldown;

    public Herobrine(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public static AttributeSupplier.Builder attributes() {
        return Them.attributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.MOVEMENT_SPEED, 0.09)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.FOLLOW_RANGE, 200.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 4.0);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (teleportCooldown > 0) {
            teleportCooldown--;
        } else if (getTarget() instanceof Player target && target.isAlive()
                && distanceToSqr(target) > TELEPORT_MIN_DIST * TELEPORT_MIN_DIST) {
            teleportNear(target);
        }
    }
    
    private void teleportNear(Player target) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double x = target.getX() + (random.nextDouble() - 0.5) * TELEPORT_RANGE * 2;
            double z = target.getZ() + (random.nextDouble() - 0.5) * TELEPORT_RANGE * 2;
            BlockPos column = BlockPos.containing(x, target.getY(), z);
            OptionalInt standY = Ground.standY(level(), column.getX(), column.getY(), column.getZ());
            if (standY.isPresent()) {
                teleportTo(x, standY.getAsInt(), z);
                teleportCooldown = TELEPORT_INTERVAL;
                return;
            }
        }
        teleportCooldown = 20;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("TeleportCooldown", teleportCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        teleportCooldown = tag.getInt("TeleportCooldown");
    }
}
