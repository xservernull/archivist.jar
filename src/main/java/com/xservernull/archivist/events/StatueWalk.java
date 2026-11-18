// aether: the statues (old statueeventhandler).

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Statue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

final class StatueWalk implements QuickEvent {
    static final String ID = "statue_walk";

    private static final Logger LOG = LogUtils.getLogger();

    private static final int COUNT = 3;
    private static final int FROZEN_TICKS = 20 * 5;
    private static final int WALK_TICKS = 20 * 15;
    private static final int DARKNESS_TICKS = 20 * 4;
    private static final int RETARGET_EVERY = 10;
    private static final int PLACE_EVERY = 10;
    private static final int PLACE_UNTIL = 20 * 4;
    private static final double MIN_RADIUS = 10.0;
    private static final double MAX_RADIUS = 50.0;
    private static final int MAX_HEIGHT_OFFSET = 10;
    private static final int SPOT_ATTEMPTS = 12;
    private static final double SEARCH_RADIUS = 120.0;

    @Override public String id() { return ID; }

    @Override
    public void start(ServerPlayer player) {
        discardTagged(player); // statues left by a crash or a reload
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        if (age <= PLACE_UNTIL && age % PLACE_EVERY == 0) {
            int missing = COUNT - find(level, player).size();
            for (int i = 0; i < missing; i++) spawn(level, player);
        }
        if (age == FROZEN_TICKS) {
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_TICKS, 0));
            LOG.info("[events] aether: {} statue(s) start walking at {}", find(level, player).size(), player.getName().getString());
        }
        if (age >= FROZEN_TICKS && age % RETARGET_EVERY == 0) {
            for (Statue statue : find(level, player)) {
                statue.setHunting(true);
                if (statue.getTarget() != player) statue.setTarget(player);
            }
        }
        return age >= FROZEN_TICKS + WALK_TICKS;
    }

    @Override
    public void stop(ServerPlayer player) {
        discardTagged(player);
    }

    // ---- spawning ----

    private static void spawn(ServerLevel level, ServerPlayer player) {
        Vec3 at = pickSpot(level, player);
        if (at == null) return;
        Statue statue = Mobs.STATUE.get().create(level);
        if (statue == null) return;
        statue.moveTo(at.x, at.y, at.z, player.getRandom().nextFloat() * 360.0F, 0.0F);
        statue.addTag(tag(player));
        level.addFreshEntity(statue);
    }

    private static Vec3 pickSpot(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double radius = MIN_RADIUS + player.getRandom().nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
            if (!Ground.isLoaded(level, x, z)) continue;
            int fromY = player.getBlockY() + player.getRandom().nextInt(MAX_HEIGHT_OFFSET * 2 + 1) - MAX_HEIGHT_OFFSET;
            fromY = Mth.clamp(fromY, level.getMinBuildHeight() + 4, level.getMaxBuildHeight() - 4);
            OptionalInt y = Ground.standY(level, x, fromY, z);
            if (y.isEmpty()) continue;
            BlockPos floor = new BlockPos(x, y.getAsInt() - 1, z);
            // standY also "finds" the bottom of the world over the void: the block under the feet must be real ground
            if (!level.getBlockState(floor).isSolid() || y.getAsInt() < player.getBlockY() - 30) continue;
            return new Vec3(x + 0.5, y.getAsInt(), z + 0.5);
        }
        return null;
    }

    // ---- finding them ----

    private static String tag(ServerPlayer player) {
        return "archivist_statue_" + player.getUUID();
    }

    private static List<Statue> find(ServerLevel level, ServerPlayer player) {
        String tag = tag(player);
        return level.getEntities(Mobs.STATUE.get(), player.getBoundingBox().inflate(SEARCH_RADIUS), s -> s.getTags().contains(tag));
    }

    private static void discardTagged(ServerPlayer player) {
        String tag = tag(player);
        List<ServerLevel> levels = new ArrayList<>();
        levels.add(player.serverLevel());
        if (player.getServer() != null) {
            ServerLevel aether = player.getServer().getLevel(Archivist.AETHER);
            if (aether != null && !levels.contains(aether)) levels.add(aether);
        }
        List<Entity> found = new ArrayList<>();
        for (ServerLevel level : levels) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Statue && e.getTags().contains(tag)) found.add(e);
            }
        }
        found.forEach(Entity::discard);
    }
}
