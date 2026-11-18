// dimension progression (old dimensionprogressionhandler).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Texts;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

import java.util.List;

final class DimensionProgress {
    private DimensionProgress() {}

    private static final Logger LOG = LogUtils.getLogger();

    static final String ID = "rd_summon";
    static final String ALL_FLAG = "rd_all_dims";
    static final String VISIT = "dim_visited:";

    static final List<String> REQUIRED = List.of(
            "childhoodhome", "aether", "library", "old_nether", "xor_world", "m4kb_world", "mansion_dim",
            "echo_chamber", "far_lands", "bedrock_far_lands", "villllage", "neverforgetyou", "quietplace",
            "infdev20100227");

    private static final int FADE_TICKS = 40, ARRIVE_TICKS = 60;
    private static final int CHECK_EVERY = 20;
    private static final double RD_X = 128.5, RD_Y = 46, RD_Z = 126.5;

    private static final ResourceKey<Level> RD132011 =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "rd132011"));

    static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) visit(player, e.getTo().location());
        });
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) check(player);
        });
    }

    private static void visit(ServerPlayer player, ResourceLocation to) {
        if (!to.getNamespace().equals(Archivist.MOD_ID)) return;
        EventState.Memory memory = Events.memory(player);
        String flag = VISIT + to.getPath();
        if (memory.hasFlag(flag)) return;
        memory.setFlag(flag);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.0F);
        player.sendSystemMessage(Texts.tr("archivist.msg.dim_unlocked", "%s unlocked", to.getPath().replace('_', ' ')));
        LOG.info("[progress] {} first visit to {}", player.getName().getString(), to);
    }

    private static void check(ServerPlayer player) {
        if (player.tickCount % CHECK_EVERY != 0 || !player.isAlive() || player.isSpectator()) return;
        if (player.level().dimension().equals(RD132011) || Events.running(player) != null) return;
        EventState.Memory memory = Events.memory(player);
        if (memory.hasFlag(ALL_FLAG)) return;
        for (String path : REQUIRED) {
            if (!memory.hasFlag(VISIT + path)) return;
        }
        LOG.info("[progress] {} has visited every dimension, rd132011 in {} ticks", player.getName().getString(), FADE_TICKS);
        Events.start(player, ID);
    }

    static final class Summon implements QuickEvent {
        @Override public String id() { return ID; }

        @Override
        public void start(ServerPlayer player) {
            EventNet.fx(player, "blackout", FADE_TICKS);
        }

        @Override
        public boolean tick(ServerPlayer player, int age) {
            if (age < FADE_TICKS) return false;
            MinecraftServer server = player.getServer();
            ServerLevel level = server == null ? null : server.getLevel(RD132011);
            if (level == null) {
                LOG.warn("[progress] dimension {} not found", RD132011.location());
                EventNet.stopFx(player);
            } else if (Warp.place(player, level, RD_X, RD_Y, RD_Z)) {
                player.setDeltaMovement(0, 0, 0);
                player.hurtMarked = true;
                EventNet.fx(player, "infdev_arrive", ARRIVE_TICKS);
            } else {
                EventNet.stopFx(player);
            }
            Events.memory(player).setFlag(ALL_FLAG); // once, also when the move failed (no retry loop)
            return true;
        }
    }
}
