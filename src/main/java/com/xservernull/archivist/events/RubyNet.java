// the two packets of the rd132011 owner terminal.

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.client.OwnerTerminalClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class RubyNet {
    private RubyNet() {}

    public record OpenTerminal() implements CustomPacketPayload {
        public static final Type<OpenTerminal> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "open_terminal"));
        public static final StreamCodec<ByteBuf, OpenTerminal> CODEC = StreamCodec.unit(new OpenTerminal());

        @Override public Type<OpenTerminal> type() { return TYPE; }
    }

    public record Erase() implements CustomPacketPayload {
        public static final Type<Erase> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "terminal_erase"));
        public static final StreamCodec<ByteBuf, Erase> CODEC = StreamCodec.unit(new Erase());

        @Override public Type<Erase> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(OpenTerminal.TYPE, OpenTerminal.CODEC, (payload, context) -> SafeClient.run("ruby_terminal", OwnerTerminalClient::launch));
        registrar.playToServer(Erase.TYPE, Erase.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) RubyDung.erase(player);
        });
    }

    static void openTerminal(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new OpenTerminal());
    }
}
