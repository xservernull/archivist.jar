package com.xservernull.archivist.client.shader;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;

/**
 * The "old game" dimensions (rd132011, infdev20100227)
 */
public record OldLook(float r, float g, float b, boolean flatSky) {
    public static final float FOG_START = 0.8f;
    public static final ResourceLocation BLANK =
            ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "textures/environment/blank.png");

    private static final Map<ResourceKey<Level>, OldLook> BY_DIMENSION = Map.of(
            key("rd132011"), new OldLook(0f, 0f, 1f, true),
            key("infdev20100227"), new OldLook(0.706f, 0.706f, 1f, false));

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path));
    }

    public static OldLook get(ResourceKey<Level> dimension) {
        return BY_DIMENSION.get(dimension);
    }
    public static OldLook current() {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : BY_DIMENSION.get(level.dimension());
    }
}
