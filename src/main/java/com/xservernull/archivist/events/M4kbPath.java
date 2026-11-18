// m4kb_world path: not an event any more, a rule of the dimension.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

final class M4kbPath {
    private M4kbPath() {}

    private static final ResourceLocation M4KB_WORLD = ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "m4kb_world");

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player) || !player.isAlive()) return;
            if (!player.level().dimension().location().equals(M4KB_WORLD)) return;
            BlockTrail.lay(player, CubeBlocks::m4kb);
        });
    }
}
