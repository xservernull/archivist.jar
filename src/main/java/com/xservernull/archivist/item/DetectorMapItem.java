package com.xservernull.archivist.item;

import com.xservernull.archivist.Texts;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Detector map item (id detectormap). Doesn't do anything by itself, it just has the isHeld check
 * that the server senders and DetectorMapOverlay use to draw the radar, plus a tooltip.
 */
public class DetectorMapItem extends Item {
    public DetectorMapItem() {
        super(new Item.Properties().stacksTo(1).fireResistant());
    }

    public static boolean isHeld(Player player) {
        return player.getMainHandItem().getItem() instanceof DetectorMapItem
                || player.getOffhandItem().getItem() instanceof DetectorMapItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Texts.tr("archivist.tooltip.radar", "radar"));
    }
}