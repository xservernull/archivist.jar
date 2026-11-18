package com.xservernull.archivist.item;

import com.xservernull.archivist.events.AdminPanelNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Admin panel item (id adminpanel_exe). Using it makes the server call AdminPanelNet.open,
 * which starts the admin panel window on the player's own machine.
 */
public class AdminPanelItem extends Item {
    public AdminPanelItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player instanceof ServerPlayer server) AdminPanelNet.open(server);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}