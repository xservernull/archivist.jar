// phantom footsteps in archivist:quietplace (port of the footstep part of the old
// quietplacehandler): every 200 to 600 ticks a step sound plays on the ground somewhere around

package com.xservernull.archivist.events;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.Ground;
import com.xservernull.archivist.Sounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Archivist.MOD_ID)
public final class PhantomFootsteps {
    private PhantomFootsteps() {}

    private static final String KEY = "phantom_footsteps";
    private static final int CHECK_TICKS = 20;
    private static final int MIN_INTERVAL = 200, MAX_INTERVAL = 600;
    private static final double MIN_DISTANCE = 8.0, MAX_DISTANCE = 16.0;
    private static final float MIN_VOLUME = 0.3F, MAX_VOLUME = 0.5F;
    private static final float MIN_PITCH = 0.7F, MAX_PITCH = 1.2F;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % CHECK_TICKS != 0) return;
        if (!(player.level() instanceof ServerLevel level) || !level.dimension().equals(Archivist.QUIET_PLACE)) return;

        EventState.Memory memory = Events.memory(player);
        long now = level.getGameTime();
        long until = memory.cooldownUntil(KEY);
        if (until == 0L) {
            memory.setCooldown(KEY, now + interval(player.getRandom()));
            return;
        }
        if (until > now) return;
        if (play(level, player)) memory.setCooldown(KEY, now + interval(player.getRandom()));
    }

    private static boolean play(ServerLevel level, ServerPlayer player) {
        RandomSource random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        if (!Ground.isLoaded(level, x, z)) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (level.getBlockState(new BlockPos(x, y - 1, z)).isAir()) return false;
        float volume = MIN_VOLUME + random.nextFloat() * (MAX_VOLUME - MIN_VOLUME);
        float pitch = MIN_PITCH + random.nextFloat() * (MAX_PITCH - MIN_PITCH);
        level.playSound(null, x + 0.5, y, z + 0.5, Sounds.FOOTSTEPS.get(), SoundSource.AMBIENT, volume, pitch);
        return true;
    }

    private static int interval(RandomSource random) {
        return MIN_INTERVAL + random.nextInt(MAX_INTERVAL - MIN_INTERVAL);
    }
}