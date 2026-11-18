// no fire in the dimensions where it would burn the work (old entityspawner: the damage hook
// and the fire-placement hook, with its no_flammable_dimensions list): childhoodhome,

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;

final class FireRules {
    private FireRules() {}

    private static final Set<String> NO_FIRE = Set.of(
            "childhoodhome", "infdev20100227", "library", "aether", "bedrock_far_lands", "echo_chamber", "neverforgetyou");

    static void listen() {
        NeoForge.EVENT_BUS.addListener(FireRules::onDamage);
        NeoForge.EVENT_BUS.addListener(FireRules::onPlace);
    }

    private static boolean noFire(Level level) {
        ResourceLocation id = level.dimension().location();
        return id.getNamespace().equals(Archivist.MOD_ID) && NO_FIRE.contains(id.getPath());
    }

    private static void onDamage(LivingIncomingDamageEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.level().isClientSide() || !noFire(entity.level())) return;
        if (!e.getSource().is(DamageTypeTags.IS_FIRE)) return;
        e.setCanceled(true);
        entity.clearFire();
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof Level level) || level.isClientSide() || !noFire(level)) return;
        BlockState placed = e.getPlacedBlock();
        if (placed.is(Blocks.FIRE) || placed.is(Blocks.SOUL_FIRE)) e.setCanceled(true);
    }
}