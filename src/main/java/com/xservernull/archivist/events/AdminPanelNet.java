// the two packets of the admin panel window (client.adminpanelclient, jar adminpanel.jar) and
// the server half of it.

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.client.AdminPanelClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.util.List;

public final class AdminPanelNet {
    private AdminPanelNet() {}

    private static final Logger LOG = LogUtils.getLogger();

    public static final List<String> DIMENSIONS = List.of(
            "overworld", "childhoodhome", "library", "old_nether", "xor_world", "m4kb_world", "mansion_dim",
            "echo_chamber", "far_lands", "bedrock_far_lands", "villllage", "neverforgetyou", "quietplace",
            "rd132011", "infdev20100227", "aether");

    static final String PIT = "thepit";

    private static final double LIBRARY_X = 22.5, LIBRARY_Y = 61, LIBRARY_Z = 9.5;
    private static final double RD_X = 128.5, RD_Y = 46, RD_Z = 126.5;

    private static final StreamCodec<ByteBuf, List<String>> STRINGS = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public record Open(List<String> dimensions, List<String> open, List<String> logs, List<String> hints) implements CustomPacketPayload {
        public static final Type<Open> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "admin_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
                STRINGS, Open::dimensions,
                STRINGS, Open::open,
                STRINGS, Open::logs,
                STRINGS, Open::hints,
                Open::new);

        @Override public Type<Open> type() { return TYPE; }
    }

    public record Go(String dimension) implements CustomPacketPayload {
        public static final Type<Go> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "admin_go"));
        public static final StreamCodec<ByteBuf, Go> CODEC = ByteBufCodecs.STRING_UTF8.map(Go::new, Go::dimension);

        @Override public Type<Go> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, context) -> SafeClient.run("admin_panel", () -> AdminPanelClient.open(payload)));
        registrar.playToServer(Go.TYPE, Go.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) go(player, payload.dimension());
        });
    }

    public static List<String> openDimensions(ServerPlayer player) {
        return DIMENSIONS;
    }

    public static void open(ServerPlayer player) {
        List<String> open = openDimensions(player);
        PacketDistributor.sendToPlayer(player, new Open(DIMENSIONS, open, AdminPanelText.logs(open), AdminPanelText.hints(open)));
    }

    private static void go(ServerPlayer player, String id) {
        if (id.equals(PIT)) { // the library hint's command; no dimension is open or needed for it
            if (InfdevEntry.openNear(player)) LOG.info("[panel] {} -> thepit", player.getGameProfile().getName());
            return;
        }
        if (!DIMENSIONS.contains(id)) {
            LOG.warn("[panel] {} asked for {}, which is not in the index", player.getGameProfile().getName(), id);
            return;
        }
        if (warp(player, id)) LOG.info("[panel] {} -> {}", player.getGameProfile().getName(), id);
    }

    static boolean warp(ServerPlayer player, String id) {
        MinecraftServer server = player.getServer();
        ServerLevel level = server == null ? null : server.getLevel(key(id));
        if (level == null) {
            LOG.warn("[panel] dimension {} not found", id);
            return false;
        }
        switch (id) {
            case "overworld" -> Warp.toOverworld(player);
            case "quietplace" -> Warp.toQuietPlace(player);
            case "childhoodhome" -> Warp.toChildhoodHome(player);
            case "aether" -> AetherEntry.enter(player);
            case "mansion_dim" -> MansionWorld.toMansion(player);
            case "library" -> Warp.place(player, level, LIBRARY_X, LIBRARY_Y, LIBRARY_Z);
            case "rd132011" -> Warp.place(player, level, RD_X, RD_Y, RD_Z);
            case "old_nether" -> OldNetherReactor.enter(player);
            default -> toSpawnColumn(player, level);
        }
        return true;
    }

    private static void toSpawnColumn(ServerPlayer player, ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        level.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
        double y = Ground.openY(level, spawn.getX(), spawn.getZ(), spawn.getY());
        Warp.place(player, level, spawn.getX() + 0.5, y, spawn.getZ() + 0.5);
    }

    private static ResourceKey<Level> key(String id) {
        return id.equals("overworld") ? Level.OVERWORLD
                : ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, id));
    }
}