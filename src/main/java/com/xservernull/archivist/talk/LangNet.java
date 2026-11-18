// packet the client uses to tell the server which archivist language it picked
// the code is checked against the config languages before it is used
package com.xservernull.archivist.talk;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class LangNet {
    private LangNet() {}

    public record Set(String code) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "language"));
        public static final StreamCodec<ByteBuf, Set> CODEC = ByteBufCodecs.stringUtf8(16).map(Set::new, Set::code);

        @Override public Type<Set> type() { return TYPE; }
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(Set.TYPE, Set.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) Talk.setLanguage(player, payload.code());
        });
    }
}
