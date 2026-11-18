// mansion_dim: the hunt (old mansiondrawinghandler).

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.entity.MansionDrawing;
import com.xservernull.archivist.ArchivistConfig;
import com.xservernull.archivist.entity.Mobs;
import com.xservernull.archivist.entity.Tina;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

final class MansionHunt implements QuickEvent {
    static final String ID = "mansion_hunt";

    private static final Logger LOG = LogUtils.getLogger();

    private static final int TRY_EVERY = 10;
    private static final int DRAWINGS_UNTIL = 100;
    private static final int TINA_AT = 800;
    private static final int TINA_FORCE_AT = TINA_AT + 20;
    private static final double TINA_MIN = 12.0, TINA_MAX = 20.0;
    private static final double DRAWING_MIN = 6.0, DRAWING_MAX = 40.0;
    private static final int SPOT_ATTEMPTS = 24;
    private static final String TINAS_FLAG = "mansion_tinas";
    private static final String DRAWING_FLAG = "mansion_drawing_";

    @Override public String id() { return ID; }

    @Override
    public void start(ServerPlayer player) {
        discardOwned(player);
        EventState.Memory memory = Events.memory(player);
        memory.clearFlag(TINAS_FLAG);
        for (int kind = 0; kind < 3; kind++) memory.clearFlag(DRAWING_FLAG + kind);
    }

    @Override
    public boolean tick(ServerPlayer player, int age) {
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(MansionWorld.MANSION)) return true; // the floors only exist there
        if (age % TRY_EVERY != 0) return false;
        if (age <= DRAWINGS_UNTIL) placeDrawings(level, player);
        if (age >= TINA_AT && age <= TINA_FORCE_AT) spawnTinas(level, player, age >= TINA_FORCE_AT);
        if (age > TINA_FORCE_AT && Events.memory(player).hasFlag(TINAS_FLAG) && aliveTinas(level, player.getUUID()) == 0) {
            Events.memory(player).clearFlag(TINAS_FLAG);
            player.sendSystemMessage(Texts.tr("archivist.msg.silence", "\u00a77Silence."));
            LOG.info("[events] mansion: {} killed all Tinas", player.getName().getString());
            return true;
        }
        return false;
    }

    @Override
    public void stop(ServerPlayer player) {
        discardOwned(player);
    }

    // ---------------------------------------------------------------- drawings

    private static void placeDrawings(ServerLevel level, ServerPlayer player) {
        EventState.Memory memory = Events.memory(player);
        for (int kind = 0; kind < 3; kind++) {
            if (memory.hasFlag(DRAWING_FLAG + kind)) continue;
            Vec3 spot = floorSpot(level, player);
            if (spot == null) continue;
            // 0.05 above the floor: the drawing is a flat quad lying on it
            level.addFreshEntity(MansionDrawing.create(level, spot.x, spot.y + 0.05, spot.z, kind,
                    MansionDrawing.FACE_FLOOR, player.getUUID()));
            memory.setFlag(DRAWING_FLAG + kind);
            LOG.info("[events] mansion: drawing {} placed at {} for {}", kind, spot, player.getName().getString());
        }
    }

    private static Vec3 floorSpot(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double radius = DRAWING_MIN + player.getRandom().nextDouble() * (DRAWING_MAX - DRAWING_MIN);
            int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
            if (!Ground.isLoaded(level, x, z)) continue;
            List<Integer> floors = new ArrayList<>();
            for (int y = MansionWorld.FIRST_Y; y <= MansionWorld.LAST_Y; y++) {
                if (MansionWorld.standable(level, new net.minecraft.core.BlockPos(x, y, z))) floors.add(y);
            }
            if (floors.isEmpty()) continue;
            int y = floors.get(player.getRandom().nextInt(floors.size()));
            return new Vec3(x + 0.5, y, z + 0.5);
        }
        return null;
    }

    // ---------------------------------------------------------------- Tinas

    private static void spawnTinas(ServerLevel level, ServerPlayer player, boolean force) {
        for (int i = aliveTinas(level, player.getUUID()); i < ArchivistConfig.mansionTinas(); i++) {
            Vec3 at = tinaSpot(level, player);
            if (at == null) {
                if (!force) return;
                at = player.position().add(8.0, 0.0, 0.0); // last resort: beside the player, whose chunk is loaded
            }
            Tina tina = Mobs.TINA.get().create(level);
            if (tina == null) return;
            tina.moveTo(at.x, at.y, at.z, player.getRandom().nextFloat() * 360.0F, 0.0F);
            tina.setOwner(player.getUUID());
            level.addFreshEntity(tina);
            tina.startChase();
            Events.memory(player).setFlag(TINAS_FLAG);
        }
        LOG.info("[events] mansion: {} Tina(s) hunting {}", aliveTinas(level, player.getUUID()), player.getName().getString());
    }

    private static Vec3 tinaSpot(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            double radius = TINA_MIN + player.getRandom().nextDouble() * (TINA_MAX - TINA_MIN);
            int x = Mth.floor(player.getX() + Math.cos(angle) * radius);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * radius);
            if (!Ground.isLoaded(level, x, z)) continue;
            OptionalInt y = Ground.standY(level, x, player.getBlockY() + 2, z);
            if (y.isPresent()) return new Vec3(x + 0.5, y.getAsInt(), z + 0.5);
        }
        return null;
    }

    private static int aliveTinas(ServerLevel level, UUID owner) {
        return level.getEntities(EntityTypeTest.forClass(Tina.class), t -> t.isOwnedBy(owner) && t.isAlive()).size();
    }

    private static void discardOwned(ServerPlayer player) {
        ServerLevel level = player.getServer() == null ? null : player.getServer().getLevel(MansionWorld.MANSION);
        if (level == null) return;
        UUID id = player.getUUID();
        for (Tina t : level.getEntities(EntityTypeTest.forClass(Tina.class), t -> t.isOwnedBy(id))) t.discard();
        for (MansionDrawing d : level.getEntities(EntityTypeTest.forClass(MansionDrawing.class), d -> d.isOwnedBy(id))) d.discard();
    }
}
