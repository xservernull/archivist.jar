// clears any structure start from library chunks on load so villages never show up

package com.xservernull.archivist.events.library;

import com.xservernull.archivist.Archivist;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Map;

@EventBusSubscriber(modid = Archivist.MOD_ID)
public final class LibraryStructureBlocker {
    private static final ResourceKey<Level> LIBRARY =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "library"));

    private LibraryStructureBlocker() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(LIBRARY)) return;
        ChunkAccess chunk = event.getChunk();
        if (!chunk.getAllStarts().isEmpty()) {
            chunk.setAllStarts(Map.of());
        }
    }
}
