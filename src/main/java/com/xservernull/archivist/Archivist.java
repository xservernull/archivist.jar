package com.xservernull.archivist;

import com.xservernull.archivist.block.ModBlocks;
import net.neoforged.fml.ModContainer;
import com.xservernull.archivist.item.ModItems;
import com.xservernull.archivist.dimensions.QuietPlace;
import com.xservernull.archivist.dimensions.Realm;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Toast;
import com.xservernull.archivist.events.Events;
import com.xservernull.archivist.events.neverforgetyou.NfyEvent;
import com.xservernull.archivist.talk.Talk;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/**
 * Entry point: loads the mod (and with it, archivist.mixins.json), wires the Q&A glue
 * in {@code talk}, Toast and the other mobs in {@code entity}, the blocks in {@code block}, the neverforgetyou event (and its packets) in {@code events.neverforgetyou}, the dimension generator and the Quiet Place entity freeze in {@code dimensions},
 * and holds the few keys the client mixins read.
 */
@Mod(Archivist.MOD_ID)
public class Archivist {
    public static final String MOD_ID = "archivist";

    public static final ResourceKey<Level> QUIET_PLACE      = dimension("quietplace");
    public static final ResourceKey<Level> NEVER_FORGET_YOU = dimension("neverforgetyou");
    public static final ResourceKey<Level> ECHO_CHAMBER     = dimension("echo_chamber");
    public static final ResourceKey<Level> LIBRARY          = dimension("library");
    public static final ResourceKey<Level> AETHER           = dimension("aether");
    public static final ResourceLocation BLOOD_RAIN = id("textures/environment/rain_blood.png");

    public Archivist(IEventBus modBus, ModContainer container) {
        ArchivistConfig.register(container);
        Talk.register(modBus);
        Toast.register(modBus);
        NfyEvent.register(modBus);
        Events.register(modBus);
        Sounds.register(modBus);
        ModBlocks.register(modBus);
        ModItems.register(modBus);
        Mobs.register(modBus);
        Realm.register(modBus);
        QuietPlace.register();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, id(path));
    }
}