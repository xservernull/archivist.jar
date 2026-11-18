// where a player wakes up after dying in one of the mod's dimensions (the part of the old
// respawnrouter that the new code did not have yet; mansion_dim, echo_chamber, old_nether and

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

import java.util.Map;

final class RespawnRules {
    private RespawnRules() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final String DIED = "died_in_";

    private static final Map<String, Boolean> RULES = Map.of(
            "library", true,
            "childhoodhome", true,
            "bedrock_far_lands", true,
            "villllage", true,
            "rd132011", true,
            "aether", false,
            "neverforgetyou", false,
            "far_lands", false,
            "quietplace", false);

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RespawnRules::onDeath);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RespawnRules::onRespawn);
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        ResourceLocation dimension = player.level().dimension().location();
        EventState.Memory memory = Events.memory(player);
        for (String path : RULES.keySet()) memory.clearFlag(DIED + path);
        if (dimension.getNamespace().equals(Archivist.MOD_ID) && RULES.containsKey(dimension.getPath())) {
            memory.setFlag(DIED + dimension.getPath());
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        EventState.Memory memory = Events.memory(player);
        String path = null;
        for (String candidate : RULES.keySet()) {
            if (memory.hasFlag(DIED + candidate)) {
                memory.clearFlag(DIED + candidate);
                path = candidate;
            }
        }
        MinecraftServer server = player.getServer();
        if (path == null || server == null) return;
        String diedIn = path;
        server.execute(() -> {
            if (!player.isAlive() || player.isSpectator()) return;
            if (RULES.get(diedIn)) {
                if (!player.level().dimension().equals(Level.OVERWORLD)) {
                    LOG.info("[respawn] {} died in {}, sent to the overworld", player.getName().getString(), diedIn);
                    Warp.toOverworld(player);
                }
                return;
            }
            ResourceLocation here = player.level().dimension().location();
            if (here.getNamespace().equals(Archivist.MOD_ID) && here.getPath().equals(diedIn)) return; // already there
            LOG.info("[respawn] {} died in {}, respawned there", player.getName().getString(), diedIn);
            switch (diedIn) {
                case "aether" -> AetherEntry.enter(player);
                case "quietplace" -> Warp.toQuietPlace(player);
                case "neverforgetyou" -> DimensionEntries.later(player, Archivist.NEVER_FORGET_YOU);
                default -> DimensionEntries.later(player, DimensionEntries.FAR_LANDS_KEY);
            }
        });
    }
}