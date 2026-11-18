// block item that adds a few lore lines to its tooltip
package com.xservernull.archivist.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

final class LoreBlockItem extends BlockItem {
    private final List<Component> lore;

    LoreBlockItem(Block block, Component... lore) {
        super(block, new Item.Properties());
        this.lore = List.of(lore);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.addAll(lore);
    }
}