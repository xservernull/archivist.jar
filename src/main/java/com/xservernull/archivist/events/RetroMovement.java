// old-game physics in xor_world and m4kb_world (port of the old retrodimmovement): walk speed
// 0.1 -> 0.08 and gravity 0.08 -> 0.03 (floaty jumps and falls).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class RetroMovement {
    private RetroMovement() {}

    private static final ResourceKey<Level> XOR_WORLD  = dimension("xor_world");
    private static final ResourceKey<Level> M4KB_WORLD = dimension("m4kb_world");

    private static final ResourceLocation SPEED_ID   = id("retro_speed");
    private static final ResourceLocation GRAVITY_ID = id("retro_gravity");

    private static final double SPEED_AMOUNT = -0.2;
    private static final double GRAVITY_AMOUNT = -0.625;

    public static boolean isRetro(ResourceKey<Level> dim) {
        return dim == XOR_WORLD || dim == M4KB_WORLD;
    }

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> sync(e.getEntity(), e.getTo()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> sync(e.getEntity(), e.getEntity().level().dimension()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> sync(e.getEntity(), e.getEntity().level().dimension()));
    }

    private static void sync(Player player, ResourceKey<Level> dim) {
        boolean on = isRetro(dim);
        set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, SPEED_AMOUNT, on);
        set(player, Attributes.GRAVITY, GRAVITY_ID, GRAVITY_AMOUNT, on);
    }

    private static void set(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount, boolean on) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        if (on) {
            if (!instance.hasModifier(id)) {
                instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        } else {
            instance.removeModifier(id);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, id(path));
    }
}
