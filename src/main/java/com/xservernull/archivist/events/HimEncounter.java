// the player side of the bedrock far lands encounter (old bedrockfarlandsevent and the parts
// of himencounterevent that were not about him himself, which is now him's own business).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Him;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

public final class HimEncounter {
    private HimEncounter() {}

    private static final Logger LOG = LogUtils.getLogger();

    public static final String FIRED = "him_fired", TAMED = "him_tamed", GLITCH = "him_glitch";
    private static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "bedrock_far_lands"));
    private static final int THRESHOLD = 3;
    private static final double MIN_DIST = 20.0, MAX_DIST = 40.0;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            // dying anywhere ends his wild Him, also one that is chasing; dying in his dimension starts over from 0 grass blocks
            end(player, player.serverLevel(), true);
            if (player.level().dimension().equals(DIMENSION)) Events.memory(player).setCooldown(GLITCH, 0);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) end(player, player.serverLevel(), true);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            ServerLevel from = player.getServer() == null ? null : player.getServer().getLevel(e.getFrom());
            if (from != null) end(player, from, true);
            Him.followAcross(player, e.getFrom());
        });
    }

    public static void onGrassCleared(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        if (memory.hasFlag(TAMED) || memory.hasFlag(FIRED)) return;
        long cleared = memory.cooldownUntil(GLITCH) + 1;
        memory.setCooldown(GLITCH, cleared);
        if (cleared < THRESHOLD) return;
        ServerLevel level = player.serverLevel();
        Him him = Mobs.HIM.get().create(level);
        if (him == null) return;
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_DIST + level.random.nextDouble() * (MAX_DIST - MIN_DIST);
        him.moveTo(player.getX() + Math.cos(angle) * dist, player.getY(), player.getZ() + Math.sin(angle) * dist,
                level.random.nextFloat() * 360F, 0F);
        him.beginEncounter(player);
        level.addFreshEntity(him);
        memory.setFlag(FIRED);
        LOG.info("[him] wandering Him spawned for {}", player.getGameProfile().getName());
    }

    public static void darkness(ServerPlayer player, boolean on) {
        if (on) player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false, false));
        else player.removeEffect(MobEffects.DARKNESS);
    }

    public static void onTamed(ServerPlayer tamer) {
        Events.memory(tamer).setFlag(TAMED);
        LOG.info("[him] {} tamed Him", tamer.getGameProfile().getName());
    }

    static void onGone(ServerPlayer player) {
        Events.memory(player).clearFlag(FIRED);
        darkness(player, false);
    }

    public static void onGone(Him him, java.util.UUID playerId) {
        if (!(him.level() instanceof ServerLevel level)) return;
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player != null) onGone(player);
    }

    private static void end(ServerPlayer player, ServerLevel level, boolean discardChasing) {
        for (Him him : level.getEntities(EntityTypeTest.forClass(Him.class),
                h -> player.getUUID().equals(h.encounterId()) && !h.isTamed() && (discardChasing || !h.isChasing()))) {
            him.discard();
        }
        onGone(player);
    }
}
