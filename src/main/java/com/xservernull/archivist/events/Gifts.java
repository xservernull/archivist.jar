// what xor_world and m4kb_world events hand out instead of potion effects: either three stacks
// of very random blocks (any block in the game, mods included, minus the ones that break

package com.xservernull.archivist.events;

import com.xservernull.archivist.Texts;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

final class Gifts {
    private Gifts() {}

    static final String[] XOR_NOTES = {
            "a ^ 0 = a", "a ^ a = 0", "1 ^ 1 = 0", "0b1010 ^ 0b0110 = 0b1100", "flip one bit and everything changes",
            "it was never random", "x ^ y ^ y = x", "the cube remembers what you cancel", "0 ^ 0 ^ 0",
            "nothing here was ever added. only flipped."};
    static final String[] M4KB_NOTES = {
            "4096 bytes. that is all you get.", "the build is not finished", "every texture is 16 pixels. so are you.",
            "do not look at the file size", "somebody counted the blocks. it was not enough",
            "the world fits in a note", "grass, dirt, stone. repeat.", "this was cut for space",
            "you were supposed to leave at 4kb"};

    private static final List<String> BANNED = List.of("command_block", "structure_block", "jigsaw", "barrier", "light",
            "structure_void", "spawner", "vault", "reactor", "test_block", "test_instance");

    private static List<Item> pool;

    private static synchronized List<Item> pool() {
        if (pool != null) return pool;
        List<Item> found = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (item == Items.AIR) continue;
            String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
            boolean banned = false;
            for (String bad : BANNED) {
                if (path.contains(bad)) {
                    banned = true;
                    break;
                }
            }
            if (!banned) found.add(item);
        }
        pool = List.copyOf(found);
        return pool;
    }

    static void give(ServerPlayer player, String[] notes) {
        RandomSource random = player.getRandom();
        if (random.nextInt(10) < 6) {
            List<Item> items = pool();
            for (int i = 0; i < 3 && !items.isEmpty(); i++) {
                ItemStack stack = new ItemStack(items.get(random.nextInt(items.size())), 1);
                stack.setCount(Math.min(1 + random.nextInt(16), stack.getMaxStackSize()));
                add(player, stack);
            }
            player.sendSystemMessage(Texts.tr("archivist.event.gift.blocks", "§7it left some blocks."));
        } else {
            ItemStack note = new ItemStack(Items.PAPER);
            note.set(DataComponents.CUSTOM_NAME, Component.literal("note"));
            note.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§7" + notes[random.nextInt(notes.length)]))));
            add(player, note);
            player.sendSystemMessage(Texts.tr("archivist.event.gift.note", "§7it left a note."));
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.1F);
    }

    private static void add(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
