// two small leftovers of the old worldeventhub, no state: players take no fall damage in any
// dimension of this mod (namespace archivist); the void falls and the infdev shaft measure

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Sounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

final class WorldQuirks {
    private WorldQuirks() {}

    private static final int ONE_IN = 1000;

    static void listen() {
        NeoForge.EVENT_BUS.addListener(WorldQuirks::onFall);
        NeoForge.EVENT_BUS.addListener(WorldQuirks::onBreak);
    }

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (Archivist.MOD_ID.equals(player.level().dimension().location().getNamespace())) event.setCanceled(true);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        boolean wood = state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                || state.is(BlockTags.WOODEN_SLABS) || state.is(BlockTags.WOODEN_STAIRS);
        boolean stone = state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS)
                || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.DIAMOND_ORES);
        if (!wood && !stone) return;
        if (player.getRandom().nextInt(ONE_IN) != 0) return;
        player.playNotifySound(Sounds.SCREAM1.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
