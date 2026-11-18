package com.xservernull.archivist.item;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.block.ModBlocks;
import com.xservernull.archivist.entity.Mobs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registers the items that aren't block items, using a DeferredRegister. Ids never change. A photo is one
 * line (texture, model and lang key are all named after the id) and everything goes in the Archivist tab.
 */
public final class ModItems {
    private ModItems() {}

    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Archivist.MOD_ID);

    public static final Supplier<Item> DOG_PHOTO = photo("dog_photo");
    public static final Supplier<Item> COMEHOME = photo("comehome");
    public static final Supplier<Item> THEM_PHOTO = photo("them_photo");
    public static final Supplier<Item> DETECTOR_MAP = ITEMS.register("detectormap", DetectorMapItem::new);

    public static final Supplier<Item> FRIEND_SPAWN_EGG = ITEMS.register("friend_spawn_egg",
            () -> new DeferredSpawnEggItem(Mobs.FRIEND, 0xD3D3D3, 0x7D7D7D, new Item.Properties()));

    public static final Supplier<Item> IRON_AXE_ARCHIVE = ITEMS.register("iron_axe_archive", IronAxeArchive::new);
    public static final Supplier<Item> ADMIN_PANEL = ITEMS.register("adminpanel_exe", AdminPanelItem::new);

    static {
        ModBlocks.addToTab(ADMIN_PANEL);
        ModBlocks.addToTab(IRON_AXE_ARCHIVE);
        ModBlocks.addToTab(DETECTOR_MAP);
        ModBlocks.addToTab(FRIEND_SPAWN_EGG);
    }

    private static Supplier<Item> photo(String id) {
        Supplier<Item> item = ITEMS.register(id, () -> new PhotoItem("item/" + id));
        ModBlocks.addToTab(item);
        return item;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}