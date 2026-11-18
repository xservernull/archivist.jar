// packets for hope's conversation and the log, registered on the mod's shared payload
// registrar

package com.xservernull.archivist.events.neverforgetyou;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.client.NfyClient;
import com.xservernull.archivist.events.ChildhoodNet;
import com.xservernull.archivist.events.EventNet;
import com.xservernull.archivist.events.LibraryCorruption;
import com.xservernull.archivist.events.RubyNet;
import com.xservernull.archivist.events.TheEndTalk;
import com.xservernull.archivist.item.PhotoItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public final class NfyNet {
    private NfyNet() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, path);
    }

    public record Say(String speaker, String text, int ticks) implements CustomPacketPayload {
        public static final Type<Say> TYPE = new Type<>(id("nfy_say"));
        public static final StreamCodec<ByteBuf, Say> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Say::speaker,
                ByteBufCodecs.STRING_UTF8, Say::text,
                ByteBufCodecs.VAR_INT, Say::ticks,
                Say::new);

        @Override public Type<Say> type() { return TYPE; }
    }

    public record Ask(List<String> buttons) implements CustomPacketPayload {
        public static final Type<Ask> TYPE = new Type<>(id("nfy_ask"));
        public static final StreamCodec<ByteBuf, Ask> CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Ask::new, Ask::buttons);

        @Override public Type<Ask> type() { return TYPE; }
    }

    public record AskHud(List<String> buttons) implements CustomPacketPayload {
        public static final Type<AskHud> TYPE = new Type<>(id("nfy_ask_hud"));
        public static final StreamCodec<ByteBuf, AskHud> CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(AskHud::new, AskHud::buttons);

        @Override public Type<AskHud> type() { return TYPE; }
    }

    public record Clear() implements CustomPacketPayload {
        public static final Type<Clear> TYPE = new Type<>(id("nfy_clear"));
        public static final StreamCodec<ByteBuf, Clear> CODEC = StreamCodec.unit(new Clear());

        @Override public Type<Clear> type() { return TYPE; }
    }

    public record Pick(int index) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(id("nfy_pick"));
        public static final StreamCodec<ByteBuf, Pick> CODEC = ByteBufCodecs.VAR_INT.map(Pick::new, Pick::index);

        @Override public Type<Pick> type() { return TYPE; }
    }

    public record Read(String text) implements CustomPacketPayload {
        public static final Type<Read> TYPE = new Type<>(id("nfy_read"));
        public static final StreamCodec<ByteBuf, Read> CODEC =
                ByteBufCodecs.STRING_UTF8.map(Read::new, Read::text);

        @Override public Type<Read> type() { return TYPE; }
    }

    public record LogRequest() implements CustomPacketPayload {
        public static final Type<LogRequest> TYPE = new Type<>(id("nfy_log_request"));
        public static final StreamCodec<ByteBuf, LogRequest> CODEC = StreamCodec.unit(new LogRequest());

        @Override public Type<LogRequest> type() { return TYPE; }
    }

    public record Log(List<String> lines) implements CustomPacketPayload {
        public static final Type<Log> TYPE = new Type<>(id("nfy_log"));
        public static final StreamCodec<ByteBuf, Log> CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(Log::new, Log::lines);

        @Override public Type<Log> type() { return TYPE; }
    }

    static void register(IEventBus modBus) {
        modBus.addListener(NfyNet::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        EventNet.register(registrar);
        ChildhoodNet.register(registrar);
        PhotoItem.register(registrar);
        RubyNet.register(registrar);
        com.xservernull.archivist.events.AdminPanelNet.register(registrar);
        com.xservernull.archivist.talk.LangNet.register(registrar);
        registrar.playToClient(Say.TYPE, Say.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("nfy_say", () -> NfyClient.say(payload)));
        registrar.playToClient(Ask.TYPE, Ask.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("nfy_ask", () -> NfyClient.ask(payload)));
        registrar.playToClient(AskHud.TYPE, AskHud.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("nfy_ask_hud", () -> NfyClient.askHud(payload.buttons())));
        registrar.playToClient(Read.TYPE, Read.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("nfy_read", () -> NfyClient.read(payload)));
        registrar.playToClient(Log.TYPE, Log.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("nfy_log", () -> NfyClient.log(payload)));
        registrar.playToServer(LogRequest.TYPE, LogRequest.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) NfyEvent.sendLog(player);
        });
        registrar.playToClient(Clear.TYPE, Clear.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("nfy_clear", NfyClient::clear));
        registrar.playToServer(Pick.TYPE, Pick.CODEC, (payload, context) -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            NfyDialogue.pick(player, payload.index());
            LibraryCorruption.pick(player, payload.index());
            TheEndTalk.pick(player, payload.index());
        });
    }

    public static void say(ServerPlayer player, String speaker, String text, int ticks) {
        PacketDistributor.sendToPlayer(player, new Say(speaker, text, ticks));
    }

    public static void ask(ServerPlayer player, List<String> buttons) {
        PacketDistributor.sendToPlayer(player, new Ask(buttons));
    }

    public static void askHud(ServerPlayer player, List<String> buttons) {
        PacketDistributor.sendToPlayer(player, new AskHud(buttons));
    }

    static void read(ServerPlayer player, String text) {
        PacketDistributor.sendToPlayer(player, new Read(text));
    }

    static void log(ServerPlayer player, List<String> lines) {
        PacketDistributor.sendToPlayer(player, new Log(lines));
    }

    public static void clear(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new Clear());
    }
}
