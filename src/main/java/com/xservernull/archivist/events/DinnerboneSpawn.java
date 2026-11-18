// dinnerbone's arrival spawn (old spawnhandler): the first time a player arrives in one of
// three dimensions, one dinnerbone appears there, tied to that dimension

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.entity.Dinnerbone;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Map;

public final class DinnerboneSpawn {
    private DinnerboneSpawn() {}

    private interface Where {
        Vec3 at(ServerLevel level, ServerPlayer player);
    }

    private static final int RD_SIZE = 256, RD_FLOOR_Y = 43, BEDROCK_FLOOR_Y = 64, MANSION_DROP_Y = 500;

    private static final Map<ResourceKey<Level>, Where> PLACES = Map.of(
            dimension("bedrock_far_lands"), (level, player) -> {
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double dist = 60.0 + level.random.nextDouble() * 40.0;
                return new Vec3(player.getX() + Math.cos(angle) * dist, BEDROCK_FLOOR_Y, player.getZ() + Math.sin(angle) * dist);
            },
            dimension("rd132011"), (level, player) -> {
                int along = level.random.nextInt(RD_SIZE);
                int edge = level.random.nextInt(4);
                int x = edge == 0 ? 0 : edge == 1 ? RD_SIZE - 1 : along;
                int z = edge == 2 ? 0 : edge == 3 ? RD_SIZE - 1 : along;
                return new Vec3(x + 0.5, RD_FLOOR_Y, z + 0.5);
            },
            dimension("mansion_dim"), (level, player) -> new Vec3(player.getX(), MANSION_DROP_Y, player.getZ()));

    private static ResourceKey<Level> dimension(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path));
    }

    public static void listen() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer player)) return;
            Where where = PLACES.get(e.getTo());
            if (where == null) return;
            String flag = "dinnerbone_" + e.getTo().location().getPath();
            EventState.Memory memory = Events.memory(player);
            if (memory.hasFlag(flag)) return;
            if (spawn(player, e.getTo(), where)) memory.setFlag(flag);
        });
    }

    private static boolean spawn(ServerPlayer player, ResourceKey<Level> home, Where where) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = where.at(level, player);
        level.getChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4); // load it first, like the old mod
        Dinnerbone dinnerbone = Mobs.DINNERBONE.get().create(level);
        if (dinnerbone == null) return false;
        dinnerbone.moveTo(pos.x, pos.y, pos.z, 0F, 0F);
        dinnerbone.setHomeDimension(home);
        return level.addFreshEntity(dinnerbone);
    }
}
