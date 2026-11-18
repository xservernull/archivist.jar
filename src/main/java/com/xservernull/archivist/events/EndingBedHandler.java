// old endingbedhandler. in the end, after the archivist has risen for the player and an ending
// is recorded (theend#hastriggered, theend#hasending), right-clicking a bed does not let it

package com.xservernull.archivist.events;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;

final class EndingBedHandler {
    private EndingBedHandler() {}

    private static final Logger LOG = LogUtils.getLogger();

    private static final double LIFT = 1.0;

    static void listen() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, EndingBedHandler::onUse);
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.END) return;
        if (!(level.getBlockState(event.getPos()).getBlock() instanceof BedBlock)) return;
        if (!TheEnd.hasTriggered(player) || !TheEnd.hasEnding(player)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        BlockPos head = headOf(level, event.getPos());
        LOG.info("[the_end] {} used an ending bed at {}, moved to {}", player.getName().getString(), event.getPos(), head);
        player.teleportTo(level, head.getX() + 0.5, head.getY() + LIFT, head.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    private static BlockPos headOf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            Direction facing = state.getValue(BedBlock.FACING);
            BlockPos head = pos.relative(facing);
            if (level.getBlockState(head).getBlock() instanceof BedBlock) return head;
        }
        return pos;
    }
}