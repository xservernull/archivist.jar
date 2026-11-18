// what a death does around a them (server side only, nothing stored in static fields): a
// player dies (old them.notifyplayerdied, which nothing ever called): every them within 24

package com.xservernull.archivist.events;

import com.xservernull.archivist.entity.Them;
import com.xservernull.archivist.item.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class ThemDrops {
    private ThemDrops() {}

    private static final String PHOTO_FLAG = "them_photo_received";
    private static final double SEARCH_RADIUS = 24.0;

    public static void listen() {
        NeoForge.EVENT_BUS.addListener(ThemDrops::onDeath);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getEntity() instanceof ServerPlayer dead) {
            ServerLevel level = (ServerLevel) dead.level();
            AABB box = dead.getBoundingBox().inflate(SEARCH_RADIUS);
            for (Them them : level.getEntitiesOfClass(Them.class, box, t -> t.isAlive() && t.distanceToSqr(dead) <= SEARCH_RADIUS * SEARCH_RADIUS)) {
                them.seekWeapon();
            }
        } else if (event.getEntity() instanceof Them && event.getSource().getEntity() instanceof ServerPlayer killer) {
            EventState.Memory memory = Events.memory(killer);
            if (memory.hasFlag(PHOTO_FLAG)) return;
            memory.setFlag(PHOTO_FLAG);
            killer.addItem(new ItemStack(ModItems.THEM_PHOTO.get()));
        }
    }
}
