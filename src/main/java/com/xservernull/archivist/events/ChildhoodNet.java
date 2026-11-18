// the packets of the childhoodhome dog: open (server to client, which question to show first)
// and done (client to server, the last question was answered), and for the chat before the

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.client.InterrogationScreen;
import com.xservernull.archivist.client.NfyClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

public final class ChildhoodNet {
    private ChildhoodNet() {}

    public record Open(int question) implements CustomPacketPayload {
        public static final Type<Open> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "interrogation_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Open::question, Open::new);

        @Override public Type<Open> type() { return TYPE; }
    }

    public record Done() implements CustomPacketPayload {
        public static final Type<Done> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "interrogation_done"));
        public static final StreamCodec<ByteBuf, Done> CODEC = StreamCodec.unit(new Done());

        @Override public Type<Done> type() { return TYPE; }
    }

    public record DogAsk(List<String> buttons) implements CustomPacketPayload {
        public static final Type<DogAsk> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "dog_ask"));
        public static final StreamCodec<ByteBuf, DogAsk> CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(DogAsk::new, DogAsk::buttons);

        @Override public Type<DogAsk> type() { return TYPE; }
    }

    public record DogPick(int index) implements CustomPacketPayload {
        public static final Type<DogPick> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "dog_pick"));
        public static final StreamCodec<ByteBuf, DogPick> CODEC = ByteBufCodecs.VAR_INT.map(DogPick::new, DogPick::index);

        @Override public Type<DogPick> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, context) -> SafeClient.run("childhood_open", () -> InterrogationScreen.open(payload.question())));
        registrar.playToClient(DogAsk.TYPE, DogAsk.CODEC, (payload, context) -> SafeClient.run("dog_ask", () -> NfyClient.askDog(payload.buttons())));
        registrar.playToServer(DogPick.TYPE, DogPick.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) DogTalk.pick(player, payload.index());
        });
        registrar.playToServer(Done.TYPE, Done.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.level().dimension() == ChildhoodHome.CHILDHOOD_HOME) {
                Events.memory(player).setFlag(ChildhoodHome.DONE);
                ChildhoodHome.standDogUp(player);
            }
        });
    }

    static void openInterrogation(ServerPlayer player, int question) {
        PacketDistributor.sendToPlayer(player, new Open(question));
    }

    static void ask(ServerPlayer player, List<String> buttons) {
        PacketDistributor.sendToPlayer(player, new DogAsk(buttons));
    }
}