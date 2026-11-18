// the villlage friend event, server side (old friendswarmmanager + the egg gift of
// spawnhandler).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Friend;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class FriendSwarm {
    private FriendSwarm() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final int MAX_FRIENDS = 8, START_INTERVAL = 60, MIN_INTERVAL = 10, RAMP_TICKS = 600, TOTAL_TICKS = 600;
    private static final double MIN_RADIUS = 3.0, MAX_RADIUS = 6.0;
    private static final String EGG_FLAG = "villllage_friend_egg_given";
    private static final Component KICK = Texts.tr("archivist.msg.kick_friend", "You found a new friend!");

    private static final class Swarm {
        ResourceKey<Level> dimension;
        Vec3 origin;
        int elapsed, untilNext = START_INTERVAL, count = 1;
        final List<UUID> friends = new ArrayList<>();
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Archivist.MOD_ID);

    private static final Supplier<AttachmentType<Swarm[]>> SWARM = ATTACHMENTS.register("friend_swarm",
            () -> AttachmentType.builder(() -> new Swarm[1]).build());

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer player) tick(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> abort(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            abort(e.getEntity());
            if (e.getEntity() instanceof ServerPlayer player && e.getTo().location().getPath().equals("villllage")) giveEggs(player);
        });
        NeoForge.EVENT_BUS.addListener((LivingDeathEvent e) -> abort(e.getEntity()));
    }

    private static void giveEggs(ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        if (memory.hasFlag(EGG_FLAG)) return;
        memory.setFlag(EGG_FLAG);
        player.addItem(new ItemStack(ModItems.FRIEND_SPAWN_EGG.get(), 2));
        LOG.info("[friend] {} got 2 Friend spawn eggs", player.getName().getString());
    }

    public static void start(ServerLevel level, Vec3 origin, ServerPlayer player, Friend leader) {
        if (player == null) return;
        Swarm[] slot = player.getData(SWARM);
        if (slot[0] != null) return;
        Swarm swarm = new Swarm();
        swarm.dimension = level.dimension();
        swarm.origin = origin;
        swarm.friends.add(leader.getUUID());
        slot[0] = swarm;
        LOG.info("[friend] swarm started for {}", player.getName().getString());
    }

    private static void tick(ServerPlayer player) {
        if (!player.hasData(SWARM)) return;
        Swarm[] slot = player.getData(SWARM);
        Swarm swarm = slot[0];
        if (swarm == null) return;
        ServerLevel level = player.getServer().getLevel(swarm.dimension);
        if (level == null) {
            slot[0] = null;
            return;
        }
        swarm.elapsed++;
        if (swarm.count < MAX_FRIENDS && swarm.elapsed < RAMP_TICKS && --swarm.untilNext <= 0) {
            spawnOne(level, swarm);
            swarm.untilNext = interval(swarm.elapsed);
        }
        if (swarm.elapsed >= TOTAL_TICKS) {
            discard(level, swarm);
            slot[0] = null;
            LOG.info("[friend] swarm over, kicking {}", player.getName().getString());
            player.connection.disconnect(KICK);
        }
    }

    private static int interval(int elapsed) {
        if (elapsed >= RAMP_TICKS) return MIN_INTERVAL;
        float progress = elapsed / (float) RAMP_TICKS;
        return Math.round(START_INTERVAL - (START_INTERVAL - MIN_INTERVAL) * progress);
    }

    private static void spawnOne(ServerLevel level, Swarm swarm) {
        double angle = level.random.nextDouble() * Math.PI * 2.0;
        double dist = MIN_RADIUS + level.random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
        Friend friend = Mobs.FRIEND.get().create(level);
        if (friend == null) return;
        friend.moveTo(swarm.origin.x + Math.cos(angle) * dist, swarm.origin.y, swarm.origin.z + Math.sin(angle) * dist, 0F, 0F);
        level.addFreshEntity(friend);
        friend.playNeckSnap();
        swarm.friends.add(friend.getUUID());
        swarm.count++;
    }

    private static void discard(ServerLevel level, Swarm swarm) {
        for (UUID id : swarm.friends) {
            Entity friend = level.getEntity(id);
            if (friend != null) friend.discard();
        }
        swarm.friends.clear();
    }

    private static void abort(Object entity) {
        if (!(entity instanceof ServerPlayer player) || !player.hasData(SWARM)) return;
        Swarm[] slot = player.getData(SWARM);
        Swarm swarm = slot[0];
        if (swarm == null) return;
        slot[0] = null;
        ServerLevel level = player.getServer().getLevel(swarm.dimension);
        if (level != null) discard(level, swarm);
    }
}
