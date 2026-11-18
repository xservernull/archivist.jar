// defines toast, the library rabbit who can follow her owner and interact through dialogue.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.talk.Talk;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;
import java.util.function.Supplier;

public final class Toast extends PathfinderMob {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Archivist.MOD_ID);

    public static final Supplier<EntityType<Toast>> TYPE = ENTITY_TYPES.register("toastbunny",
            () -> EntityType.Builder.of(Toast::new, MobCategory.CREATURE).sized(1.0F, 2.0F).build("toastbunny"));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> e.put(TYPE.get(), createAttributes()));
    }

    private static AttributeSupplier createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.4)
                .add(Attributes.FOLLOW_RANGE, 16.0)
                .build();
    }
    
    public static Toast ownedBy(ServerPlayer player) {
        return player.serverLevel()
                .getEntities(EntityTypeTest.forClass(Toast.class), t -> player.getUUID().equals(t.owner))
                .stream().findFirst().orElse(null);
    }

    private UUID owner;

    public Toast(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || owner == null || tickCount % 10 != 0) return;
        Player player = level().getPlayerByUUID(owner);
        if (player != null) {
            double distance = distanceTo(player);
            if (distance > 5 && distance < 20) getNavigation().moveTo(player, 0.3);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) {
            if (owner == null) owner = serverPlayer.getUUID();
            Talk.speak(serverPlayer, null);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
    
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.getEntity() instanceof Player && super.hurt(source, amount);
    }
    
    @Override
    public boolean canBeLeashed() { return false; }

    @Override
    public boolean requiresCustomPersistence() { return true; }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }
}