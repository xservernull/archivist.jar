package com.xservernull.archivist.client.shader;

import com.xservernull.archivist.Archivist;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Map;

import static java.util.Map.entry;

/**
 * The look of each dimension, one entry each.
 */
public final class Looks {
    private static final Look RETRO = new Look().retro(0.15f).sky(0.04f, 0.04f, 0.05f);
    private static final Look QUIET = new Look()
            .light(0.85f, 0.12f, 0.30f, 60f, 500f)
            .grade(0.95f, 0.98f, 1.04f, 0.80f, 1.08f)
            .haze(0.69f, 0.72f, 0.75f, 40f, 220f)
            .ao(0.4f, 1.0f, 48f)
            .bloom(0.8f, 0.75f)
            .frame(0.30f, 0.04f);

    private static final Map<ResourceKey<Level>, Look> BY_DIMENSION = Map.ofEntries(
            entry(Archivist.QUIET_PLACE, QUIET),
            entry(key("m4kb_world"), RETRO),
            entry(key("xor_world"), RETRO),
            entry(key("echo_chamber"), QUIET),
            entry(key("library"), new Look()
                    .grade(1.25f, 0.95f, 0.70f, 0.75f, 1.20f)
                    .ao(0.9f, 1.5f, 48f)
                    .bloom(1.4f, 0.55f)
                    .frame(0.65f, 0.06f)),
            entry(key("bedrock_far_lands"), new Look()
                    .grade(0.80f, 0.90f, 1.15f, 0.55f, 1.25f)
                    .haze(0.75f, 0.85f, 1.0f, 15f, 100f)
                    .ao(0.7f, 1.5f, 48f)
                    .bloom(1.0f, 0.75f)
                    .frame(0.50f, 0.02f)
                    .warp(0.6f)),
            entry(key("far_lands"), new Look()
                    .grade(1.12f, 1.0f, 0.85f, 1.35f, 1.15f)
                    .haze(0.75f, 0.85f, 1.0f, 25f, 140f)
                    .ao(0.7f, 1.5f, 48f)
                    .bloom(1.0f, 0.75f)
                    .frame(0.35f, 0f)
                    .warp(0.25f)),
            entry(key("neverforgetyou"), new Look()
                    .grade(0.95f, 0.98f, 1.06f, 0.80f, 1.15f)
                    .haze(0.137f, 0.173f, 0.302f, 25f, 110f)
                    .ao(0.7f, 1.5f, 48f)
                    .bloom(1.5f, 0.60f)
                    .frame(0.55f, 0.04f)
                    .breathe(0.35f)),
            entry(key("aether"), new Look()
                    .grade(1.08f, 1.08f, 1.0f, 1.45f, 0.90f)
                    .haze(1f, 1f, 1f, 25f, 130f)
                    .ao(0.5f, 1.5f, 48f)
                    .bloom(1.2f, 0.80f)
                    .frame(0.15f, 0f)),
            entry(key("mansion_dim"), new Look()
                    .grade(0.80f, 1.00f, 0.88f, 0.45f, 1.20f)
                    .haze(0.02f, 0.03f, 0.02f, 5f, 28f)
                    .ao(1.0f, 1.5f, 48f)
                    .bloom(1.2f, 0.60f)
                    .frame(0.65f, 0.12f)
                    .flicker(0.08f)),
            entry(key("villllage"), new Look()
                    .grade(1.15f, 1.0f, 0.85f, 1.45f, 1.15f)
                    .haze(1.0f, 0.90f, 0.70f, 50f, 180f)
                    .ao(0.8f, 1.5f, 48f)
                    .bloom(0.9f, 0.80f)
                    .frame(0.30f, 0f)),
            entry(key("childhoodhome"), new Look()
                    .grade(1.20f, 0.98f, 0.75f, 0.50f, 0.80f)
                    .haze(0.90f, 0.52f, 0.42f, 30f, 120f)
                    .ao(0.6f, 1.5f, 48f)
                    .bloom(1.4f, 0.55f)
                    .frame(0.60f, 0.10f)
                    .flicker(0.03f)));

    private Looks() {}

    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path));
    }

    public static Look get(ResourceKey<Level> dimension) {
        return BY_DIMENSION.get(dimension);
    }
}