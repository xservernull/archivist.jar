package com.xservernull.archivist.item;

import com.xservernull.archivist.Texts;
import com.xservernull.archivist.entity.Somehow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Iron axe (id iron_axe_archive) with +6 damage and -3.1 speed. Hitting a Somehow stuns it with stun(),
 * anything else gets Slowness 254 for the same time, all done in hurtEnemy.
 */
public class IronAxeArchive extends AxeItem {
    public IronAxeArchive() {
        super(Tiers.IRON, new Item.Properties().attributes(AxeItem.createAttributes(Tiers.IRON, 6.0F, -3.1F)));
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hurt = super.hurtEnemy(stack, target, attacker);
        if (attacker.level().isClientSide()) return hurt;
        if (target instanceof Somehow somehow) {
            somehow.stun();
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, Somehow.STUN_TICKS, 254, false, true, true));
        }
        return hurt;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Texts.tr("archivist.tooltip.axe", "stunning.").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}