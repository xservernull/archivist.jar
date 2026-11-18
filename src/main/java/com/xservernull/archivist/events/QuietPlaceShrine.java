// the herobrine shrine (old herobrineshrine + shrinepattern): nine gold blocks in a 3x3
// square, a netherrack on the middle one with fire on top of it, and a torch (redstone or

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

final class QuietPlaceShrine {
    private QuietPlaceShrine() {}

    static void listen() {
        NeoForge.EVENT_BUS.addListener(QuietPlaceShrine::onPlace);
        NeoForge.EVENT_BUS.addListener(QuietPlaceShrine::onClick);
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || !(e.getLevel() instanceof ServerLevel level)) return;
        if (!partOfShrine(e.getPlacedBlock())) return;
        check(level, player, e.getPos());
    }

    private static void onClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || e.getHand() != InteractionHand.MAIN_HAND) return;
        ItemStack held = e.getItemStack();
        if (!held.is(Items.FLINT_AND_STEEL) && !held.is(Items.FIRE_CHARGE)) return;
        ServerLevel level = player.serverLevel();
        BlockPos pos = e.getPos();
        MinecraftServer server = level.getServer();
        // the fire does not exist yet when the click event runs
        server.tell(new TickTask(server.getTickCount() + 2, () -> {
            if (!player.isRemoved() && player.serverLevel() == level) check(level, player, pos);
        }));
    }

    private static void check(ServerLevel level, ServerPlayer player, BlockPos around) {
        boolean overworld = level.dimension().equals(Level.OVERWORLD);
        if (!overworld && !level.dimension().equals(Archivist.QUIET_PLACE)) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    cursor.set(around.getX() + dx, around.getY() + dy, around.getZ() + dz);
                    if (!level.getBlockState(cursor).is(Blocks.NETHERRACK) || !complete(level, cursor)) continue;
                    if (overworld) QuietPlaceHunt.enter(player);
                    else QuietPlaceHunt.complete(player);
                    return;
                }
            }
        }
    }

    private static boolean partOfShrine(BlockState state) {
        return state.is(Blocks.GOLD_BLOCK) || state.is(Blocks.NETHERRACK) || state.is(Blocks.FIRE) || torch(state);
    }

    static boolean torch(BlockState state) {
        return state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH)
                || state.is(Blocks.REDSTONE_TORCH) || state.is(Blocks.REDSTONE_WALL_TORCH);
    }

    static boolean complete(ServerLevel level, BlockPos netherrack) {
        if (!level.getBlockState(netherrack.above()).is(Blocks.FIRE)) return false;
        BlockPos center = netherrack.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.getBlockState(center.offset(dx, 0, dz)).is(Blocks.GOLD_BLOCK)) return false;
            }
        }
        return torch(level.getBlockState(center.north().above())) && torch(level.getBlockState(center.south().above()))
                && torch(level.getBlockState(center.east().above())) && torch(level.getBlockState(center.west().above()));
    }
}