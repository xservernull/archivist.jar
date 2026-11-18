// getting into and out of archivist:aether (old aetherportal, aethercastlespawnhandler,
// aetherspawn and the aether line of portaltriggers.onvoidfall): in: #enter puts the player in

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.slf4j.Logger;

final class AetherEntry {
    private AetherEntry() {}

    private static final Logger LOG = LogUtils.getLogger();

    static final String FIRST_VISIT = "aether_first_visit";

    private static final int ROOM_X = -70 + 80, ROOM_Y = 80 + 10, ROOM_Z = -66 + 60;

    static void listen() {
        NeoForge.EVENT_BUS.addListener(AetherEntry::onVoidFall);
    }

    static boolean enter(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(Archivist.AETHER);
        if (level == null) {
            LOG.warn("[aether] dimension {} not found", Archivist.AETHER.location());
            player.sendSystemMessage(Texts.tr("archivist.msg.portal_nothing", "[Archivist]: the portal hums, but nothing happens.").withStyle(ChatFormatting.RED));
            return false;
        }
        BlockPos at = hall(level);
        boolean moved = Warp.place(player, level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        EventState.Memory memory = Events.memory(player);
        if (moved && !memory.hasFlag(FIRST_VISIT)) {
            memory.setFlag(FIRST_VISIT);
        }
        return moved;
    }

    static BlockPos hall(ServerLevel level) {
        level.getChunk(ROOM_X >> 4, ROOM_Z >> 4);
        for (int dy = 0; dy <= 8; dy++) {
            BlockPos feet = new BlockPos(ROOM_X, ROOM_Y + dy, ROOM_Z);
            if (MansionWorld.standable(level, feet)) return feet;
        }
        for (int dy = 1; dy <= 8; dy++) {
            BlockPos feet = new BlockPos(ROOM_X, ROOM_Y - dy, ROOM_Z);
            if (MansionWorld.standable(level, feet)) return feet;
        }
        LOG.warn("[aether] no free spot in the castle hall near {} {} {}", ROOM_X, ROOM_Y, ROOM_Z);
        return new BlockPos(ROOM_X, ROOM_Y, ROOM_Z);
    }

    private static void onVoidFall(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().dimension() != Archivist.AETHER) return;
        if (!e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        e.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        server.execute(() -> Warp.toOverworld(player));
    }
}