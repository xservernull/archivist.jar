// the overworld herobrine (old overworldherobrineevent), the other way into the quiet place:
// on a night in the overworld he appears 28 blocks from you and hunts you.

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.Herobrine;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;

final class HerobrineStalk implements QuickEvent {
    static final String ID = "herobrine_stalk";

    private static final Logger LOG = LogUtils.getLogger();
    private static final String TAG = "archivist_stalk_";
    private static final double SPAWN_DIST = 28.0, CAPTURE_DIST = 5.0, WANDER_DIST = 128.0;

    @Override public String id() { return ID; }

    static void listen() {
        NeoForge.EVENT_BUS.addListener(HerobrineStalk::onJoin);
    }

    @Override
    public void start(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || !level.isNight()) return; // tick ends it
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            int x = Mth.floor(player.getX() + Math.cos(angle) * SPAWN_DIST);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * SPAWN_DIST);
            if (!Ground.isLoaded(level, x, z)) continue;
            var y = Ground.standY(level, x, player.getBlockY() + 8, z);
            if (y.isEmpty()) continue;
            Herobrine hb = Mobs.HEROBRINE.get().create(level);
            if (hb == null) return;
            hb.moveTo(x + 0.5, y.getAsInt(), z + 0.5, player.getYRot(), 0.0F);
            hb.addTag(TAG + player.getUUID());
            hb.setTarget(player);
            level.addFreshEntity(hb);
            LOG.info("[stalk] Herobrine at {} {} {} hunts {}", x, y.getAsInt(), z, player.getGameProfile().getName());
            return;
        }
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(Level.OVERWORLD) || !level.isNight()) return true;
        Herobrine hb = find(level, player);
        if (hb == null || !hb.isAlive()) return age > 20; // not spawned (no room) or killed
        double distSqr = hb.distanceToSqr(player);
        if (distSqr > WANDER_DIST * WANDER_DIST) return true;
        if (distSqr <= CAPTURE_DIST * CAPTURE_DIST) {
            LOG.info("[stalk] Herobrine caught {}", player.getGameProfile().getName());
            hb.discard();
            QuietPlaceHunt.enter(player);
            return true;
        }
        if (hb.getTarget() != player) hb.setTarget(player);
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        ServerLevel level = player.getServer() == null ? null : player.getServer().overworld();
        if (level == null) return;
        String tag = TAG + player.getUUID();
        for (Herobrine hb : level.getEntities(EntityTypeTest.forClass(Herobrine.class), h -> h.getTags().contains(tag))) hb.discard();
    }

    private static Herobrine find(ServerLevel level, ServerPlayer player) {
        String tag = TAG + player.getUUID();
        List<? extends Herobrine> found = level.getEntities(EntityTypeTest.forClass(Herobrine.class), h -> h.getTags().contains(tag));
        return found.isEmpty() ? null : found.get(0);
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Herobrine hb)) return;
        for (String tag : hb.getTags()) {
            if (!tag.startsWith(TAG)) continue;
            ServerPlayer owner = null;
            try {
                owner = e.getLevel().getServer().getPlayerList().getPlayer(UUID.fromString(tag.substring(TAG.length())));
            } catch (RuntimeException ignored) {
                // not one of ours
            }
            if (owner == null || !ID.equals(Events.running(owner))) e.setCanceled(true);
            return;
        }
    }
}