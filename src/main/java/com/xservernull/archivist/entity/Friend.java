// defines the friend entity and starts the friend encounter when the first one appears.

package com.xservernull.archivist.entity;

import com.xservernull.archivist.Sounds;
import com.xservernull.archivist.events.EventNet;
import com.xservernull.archivist.events.FriendSwarm;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public final class Friend extends PathfinderMob {
    private int friendSoundTick;

    public Friend(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setNoAi(true);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor accessor, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData data) {
        if (accessor.getLevel() instanceof ServerLevel level) {
            EventNet.friendPopupAll();
            Player nearest = level.getNearestPlayer(this, 64);
            FriendSwarm.start(level, position(), nearest instanceof ServerPlayer sp ? sp : null, this);
        }
        playNeckSnap();
        return super.finalizeSpawn(accessor, difficulty, reason, data);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && ++friendSoundTick >= 100) {
            friendSoundTick = 0;
            level().playSound(null, getX(), getY(), getZ(), Sounds.FRIENDSOUND.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
    }
    
    public void playNeckSnap() {
        level().playSound(null, getX(), getY(), getZ(), Sounds.NECKSNAP.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
    }
}
