package com.xservernull.archivist.item;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Texts;
import com.xservernull.archivist.client.PhotoScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * Photo item. Right-click sends an Open packet to the client and PhotoScreen shows the picture full screen,
 * using the item's own texture as the picture.
 */
public class PhotoItem extends Item {
    public record Open(String texture) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Archivist.MOD_ID, "photo_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = ByteBufCodecs.STRING_UTF8.map(Open::new, Open::texture);

        @Override public Type<Open> type() { return TYPE; }
    }

    private final String texture;

    public PhotoItem(String textureId) {
        super(new Item.Properties().stacksTo(1).fireResistant());
        this.texture = Archivist.MOD_ID + ":" + textureId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer server) {
            PacketDistributor.sendToPlayer(server, new Open(texture));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Texts.tr("archivist.tooltip.photo", "[Right-click to examine]").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(Open.TYPE, Open.CODEC, (payload, context) -> com.xservernull.archivist.events.SafeClient.run("photo", () -> PhotoScreen.open(payload.texture())));
    }
}