package com.xservernull.archivist.dimensions;

import com.xservernull.archivist.Archivist;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.Set;

/**
 * wuiet Place
 */
public final class QuietPlace {
    public static final TagKey<EntityType<?>> FREE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "quietplace_free"));
    private static final Set<EntityType<?>> BLOCKED = Set.of(
            EntityType.FROG, EntityType.TADPOLE, EntityType.ALLAY, EntityType.SNIFFER, EntityType.CAMEL,
            EntityType.WARDEN, EntityType.AXOLOTL, EntityType.GOAT, EntityType.GLOW_SQUID, EntityType.BAT);

    private QuietPlace() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(QuietPlace::onEntityTick);
        NeoForge.EVENT_BUS.addListener(QuietPlace::onFinalizeSpawn);
    }

    private static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!event.getLevel().getLevel().dimension().equals(Archivist.QUIET_PLACE)) return;
        if (BLOCKED.contains(event.getEntity().getType())) event.setSpawnCancelled(true);
    }

    private static void onEntityTick(EntityTickEvent.Pre event) {
        Entity entity = event.getEntity();
        if (!entity.level().dimension().equals(Archivist.QUIET_PLACE)) return;
        if (entity instanceof Player || entity.getType().is(FREE)) return;
        event.setCanceled(true);
    }
}