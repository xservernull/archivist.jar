// the client packets of the events framework. screenfx (effect id, duration in ticks) starts a
// temporary screen effect (client.shader.fx) on the player's client.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.client.CornerHud;
import com.xservernull.archivist.client.DetectorMapOverlay;
import com.xservernull.archivist.client.FriendPopupOverlay;
import com.xservernull.archivist.client.HerobrineHud;
import com.xservernull.archivist.client.ShrineDiagram;
import com.xservernull.archivist.client.VideoPlayer;
import com.xservernull.archivist.client.shader.Fx;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public final class EventNet {
    private EventNet() {}

    public static final String STOP = "stop";

    public record ScreenFx(String id, int ticks) implements CustomPacketPayload {
        public static final Type<ScreenFx> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "event_fx"));
        public static final StreamCodec<ByteBuf, ScreenFx> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ScreenFx::id,
                ByteBufCodecs.VAR_INT, ScreenFx::ticks,
                ScreenFx::new);

        @Override public Type<ScreenFx> type() { return TYPE; }
    }

    public record ShrineProgress(int gold, int netherrack, int torches) implements CustomPacketPayload {
        public static final Type<ShrineProgress> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "shrine_progress"));
        public static final StreamCodec<ByteBuf, ShrineProgress> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShrineProgress::gold,
                ByteBufCodecs.VAR_INT, ShrineProgress::netherrack,
                ByteBufCodecs.VAR_INT, ShrineProgress::torches,
                ShrineProgress::new);

        @Override public Type<ShrineProgress> type() { return TYPE; }
    }

    public record DetectorMarkers(List<BlockPos> positions) implements CustomPacketPayload {
        public static final Type<DetectorMarkers> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "detector_markers"));
        public static final StreamCodec<ByteBuf, DetectorMarkers> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), DetectorMarkers::positions,
                DetectorMarkers::new);

        @Override public Type<DetectorMarkers> type() { return TYPE; }
    }

    public record FriendPopup() implements CustomPacketPayload {
        public static final Type<FriendPopup> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "friend_popup"));
        public static final StreamCodec<ByteBuf, FriendPopup> CODEC = StreamCodec.unit(new FriendPopup());

        @Override public Type<FriendPopup> type() { return TYPE; }
    }

    public record Video(String clip) implements CustomPacketPayload {
        public static final Type<Video> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "video"));
        public static final StreamCodec<ByteBuf, Video> CODEC = ByteBufCodecs.STRING_UTF8.map(Video::new, Video::clip);

        @Override public Type<Video> type() { return TYPE; }
    }

    public record Objective(String text) implements CustomPacketPayload {
        public static final Type<Objective> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "objective"));
        public static final StreamCodec<ByteBuf, Objective> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Objective::text,
                Objective::new);

        @Override public Type<Objective> type() { return TYPE; }
    }

    public record HerobrineBars(int phase, int bars) implements CustomPacketPayload {
        public static final Type<HerobrineBars> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "herobrine_bars"));
        public static final StreamCodec<ByteBuf, HerobrineBars> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, HerobrineBars::phase,
                ByteBufCodecs.VAR_INT, HerobrineBars::bars,
                HerobrineBars::new);

        @Override public Type<HerobrineBars> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(ShrineProgress.TYPE, ShrineProgress.CODEC,
                (payload, context) -> SafeClient.run("shrine_progress", () -> ShrineDiagram.set(payload.gold(), payload.netherrack(), payload.torches())));
        registrar.playToClient(DetectorMarkers.TYPE, DetectorMarkers.CODEC,
                (payload, context) -> SafeClient.run("detector_markers", () -> DetectorMapOverlay.set(payload.positions())));
        registrar.playToClient(Objective.TYPE, Objective.CODEC, (payload, context) -> SafeClient.run("objective", () -> CornerHud.setObjective(payload.text())));
        registrar.playToClient(HerobrineBars.TYPE, HerobrineBars.CODEC, (payload, context) -> SafeClient.run("herobrine_bars", () -> HerobrineHud.set(payload.phase(), payload.bars())));
        registrar.playToClient(FriendPopup.TYPE, FriendPopup.CODEC, (payload, context) -> SafeClient.run("friend_popup", FriendPopupOverlay::trigger));
        registrar.playToClient(Video.TYPE, Video.CODEC, (payload, context) -> SafeClient.run("video", () -> VideoPlayer.play(payload.clip())));
        registrar.playToClient(ScreenFx.TYPE, ScreenFx.CODEC, (payload, context) -> SafeClient.run("event_fx", () -> {
            if (payload.id().equals(STOP)) Fx.stop();
            else Fx.start(payload.id(), payload.ticks());
        }));
    }

    public static void objective(ServerPlayer player, String text) {
        PacketDistributor.sendToPlayer(player, new Objective(text));
    }

    public static void herobrine(ServerPlayer player, int phase, int bars) {
        PacketDistributor.sendToPlayer(player, new HerobrineBars(phase, bars));
    }

    public static void fx(ServerPlayer player, String id, int ticks) {
        PacketDistributor.sendToPlayer(player, new ScreenFx(id, ticks));
    }

    public static void shrine(ServerPlayer player, int gold, int netherrack, int torches) {
        PacketDistributor.sendToPlayer(player, new ShrineProgress(gold, netherrack, torches));
    }

    public static void detector(ServerPlayer player, List<BlockPos> positions) {
        PacketDistributor.sendToPlayer(player, new DetectorMarkers(List.copyOf(positions)));
    }

    public static void friendPopupAll() {
        PacketDistributor.sendToAllPlayers(new FriendPopup());
    }

    public static void video(ServerPlayer player, String clip) {
        PacketDistributor.sendToPlayer(player, new Video(clip));
    }

    public static void stopVideo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Video(""));
    }

    public static void stopFx(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ScreenFx(STOP, 0));
    }
}