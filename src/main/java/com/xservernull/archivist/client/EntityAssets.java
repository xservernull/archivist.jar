package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the assets of a mob are
 */
final class EntityAssets {
    private static final Map<EntityType<?>, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, ResourceLocation> GEOMETRY = new ConcurrentHashMap<>();
    private static final Map<EntityType<?>, ResourceLocation> ANIMATIONS = new ConcurrentHashMap<>();

    private EntityAssets() {}

    static ResourceLocation texture(Entity entity) {
        return TEXTURES.computeIfAbsent(entity.getType(), type -> of("textures/entity/%s.png", entity));
    }

    static ResourceLocation geometry(Entity entity) {
        return GEOMETRY.computeIfAbsent(entity.getType(), type -> of("geo/entity/%s.geo.json", entity));
    }

    static ResourceLocation animations(Entity entity) {
        return ANIMATIONS.computeIfAbsent(entity.getType(), type -> of("animations/entity/%s.animation.json", entity));
    }

    private static ResourceLocation of(String pattern, Entity entity) {
        return ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, pattern.formatted(Mobs.id(entity)));
    }
}
