package com.xservernull.archivist.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.ArchivistConfig;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

import java.util.Set;

/**
 * The "Loud noises" slider
 */
@EventBusSubscriber(modid = Archivist.MOD_ID, value = Dist.CLIENT)
public final class LoudNoises {
    private LoudNoises() {}
    private static final Set<String> LOUD = Set.of("die", "heartbeat", "necksnap", "alarm", "glitch", "scream1",
            "tinastep", "tinaambient", "byetina", "dinnerbone/ambient", "dinnerbone/hurt", "dinnerbone/death", "dinnerbone/hoofstep");

    @SubscribeEvent
    public static void onPlay(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || !Archivist.MOD_ID.equals(sound.getLocation().getNamespace())) return;
        if (!LOUD.contains(sound.getLocation().getPath())) return;
        int percent = ArchivistConfig.loud();
        if (percent >= 100) return;
        if (percent <= 0) {
            event.setSound(null);
            return;
        }
        if (sound.getClass() != SimpleSoundInstance.class) return;
        event.setSound(new SimpleSoundInstance(sound.getLocation(), sound.getSource(),
                sound.getVolume() * percent / 100f, sound.getPitch(), SoundInstance.createUnseededRandom(),
                sound.isLooping(), sound.getDelay(), sound.getAttenuation(),
                sound.getX(), sound.getY(), sound.getZ(), sound.isRelative()));
    }
}
