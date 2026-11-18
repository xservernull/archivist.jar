// registers the mod’s entity types and automatically registers their attributes.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Archivist;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public final class Mobs {
    private Mobs() {}

    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Archivist.MOD_ID);
    
    private static final List<Consumer<EntityAttributeCreationEvent>> ATTRIBUTES = new ArrayList<>();

    public static final Supplier<EntityType<Friend>> FRIEND = living("friend", Friend::new, MobCategory.CREATURE,
            0.6F, 1.8F, b -> b, Mob::createMobAttributes);
    public static final Supplier<EntityType<Pigmen>> PIGMEN = living("pigmen", Pigmen::new, MobCategory.CREATURE,
            0.6F, 1.8F, b -> b, Mob::createMobAttributes);
    public static final Supplier<EntityType<Somehow>> SOMEHOW = living("somehow", Somehow::new, MobCategory.MONSTER,
            0.6F, 1.8F, b -> b.clientTrackingRange(64).updateInterval(2), Somehow::attributes);
    public static final Supplier<EntityType<Them>> THEM = living("them", Them::new, MobCategory.MONSTER,
            0.7F, 2.0F, b -> b.clientTrackingRange(80).updateInterval(2), Them::attributes);
    public static final Supplier<EntityType<Herobrine>> HEROBRINE = living("herobrine", Herobrine::new,
            MobCategory.MONSTER, 0.7F, 2.0F, b -> b.clientTrackingRange(128).updateInterval(2), Herobrine::attributes);
    public static final Supplier<EntityType<Tenature>> TENATURE = living("tenature", Tenature::new,
            MobCategory.CREATURE, 0.6F, 1.8F, b -> b, Tenature::attributes);
    public static final Supplier<EntityType<Dog>> DOG = living("dog", Dog::new, MobCategory.MISC,
            0.6F, 0.85F, b -> b.clientTrackingRange(10), Dog::attributes);
    public static final Supplier<EntityType<FakeDoor>> FAKE_DOOR = living("faketeleporterdoor", FakeDoor::new,
            MobCategory.MISC, 1.0F, 2.0F, b -> b, Mob::createMobAttributes);

    // GeckoLib mobs (assets named after the id: assets/archivist/{geo,animations,textures}/entity/<id>.*)
    public static final Supplier<EntityType<Tina>> TINA = living("tina", Tina::new, MobCategory.MONSTER,
            0.8F, 1.5F, b -> b.clientTrackingRange(200), Tina::attributes);
    public static final Supplier<EntityType<Him>> HIM = living("him", Him::new, MobCategory.MISC,
            0.6F, 2.6F, b -> b.clientTrackingRange(48).updateInterval(2), Him::attributes);
    public static final Supplier<EntityType<Hope>> HOPE = living("hope", Hope::new, MobCategory.MISC,
            1.0F, 1.2F, b -> b.clientTrackingRange(8).updateInterval(3), Hope::attributes);
    public static final Supplier<EntityType<ArchivistDragon>> ARCHIVIST_DRAGON = living("archivistdragon",
            ArchivistDragon::new, MobCategory.MISC, 16.0F, 16.0F, b -> b.clientTrackingRange(256),
            ArchivistDragon::attributes);
    public static final Supplier<EntityType<Statue>> STATUE = living("statue", Statue::new, MobCategory.MISC,
            0.6F, 1.8F, b -> b.clientTrackingRange(100), Statue::attributes);
    public static final Supplier<EntityType<Dinnerbone>> DINNERBONE = living("dinnerbone", Dinnerbone::new,
            MobCategory.CREATURE, 1.1F, 2.3F, b -> b, Dinnerbone::attributes);
    public static final Supplier<EntityType<CopyMob>> COPY_ARROW = living("copy_arrow", CopyMob::hostile,
            MobCategory.MONSTER, 0.6F, 1.8F, b -> b, CopyMob::hostileAttributes);
    public static final Supplier<EntityType<CopyMob>> COPY_COBWEB = living("copy_cobweb", CopyMob::hostile,
            MobCategory.MONSTER, 0.6F, 1.8F, b -> b, CopyMob::hostileAttributes);
    public static final Supplier<EntityType<CopyMob>> COPY_ZOMBIE = living("copy_zombie", CopyMob::hostile,
            MobCategory.MONSTER, 0.6F, 1.8F, b -> b, CopyMob::hostileAttributes);
    public static final Supplier<EntityType<CopyMob>> COPY_FRIENDLY = living("copy_friendly", CopyMob::friendly,
            MobCategory.CREATURE, 0.6F, 1.8F, b -> b, CopyMob::friendlyAttributes);

    public static final Supplier<EntityType<GlowOrb>> GLOW_ORB = other("glow_orb", GlowOrb::new,
            0.5F, 0.5F, b -> b.clientTrackingRange(64).noSummon());
    public static final Supplier<EntityType<MansionDrawing>> MANSION_DRAWING = other("mansion_drawing",
            MansionDrawing::new, 0.5F, 0.05F, b -> b.clientTrackingRange(32).noSummon());
    
    private static <T extends LivingEntity> Supplier<EntityType<T>> living(
            String id, EntityType.EntityFactory<T> factory, MobCategory category, float width, float height,
            UnaryOperator<EntityType.Builder<T>> tune, Supplier<AttributeSupplier.Builder> attributes) {
        Supplier<EntityType<T>> type = register(id, factory, category, width, height, tune);
        ATTRIBUTES.add(event -> event.put(type.get(), attributes.get().build()));
        return type;
    }
    
    private static <T extends Entity> Supplier<EntityType<T>> other(
            String id, EntityType.EntityFactory<T> factory, float width, float height,
            UnaryOperator<EntityType.Builder<T>> tune) {
        return register(id, factory, MobCategory.MISC, width, height, tune);
    }

    private static <T extends Entity> Supplier<EntityType<T>> register(
            String id, EntityType.EntityFactory<T> factory, MobCategory category, float width, float height,
            UnaryOperator<EntityType.Builder<T>> tune) {
        return TYPES.register(id, () -> tune.apply(EntityType.Builder.of(factory, category).sized(width, height)).build(id));
    }
    
    public static String id(Entity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> ATTRIBUTES.forEach(add -> add.accept(event)));
    }
}
