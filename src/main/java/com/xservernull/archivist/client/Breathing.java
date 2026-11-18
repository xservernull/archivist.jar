package com.xservernull.archivist.client;

import com.xservernull.archivist.Sounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * the breathing loop under the dog's questions
 */
final class Breathing extends AbstractTickableSoundInstance {
    private static final int GRACE = 10;
    private static Breathing current;

    private int awayTicks;

    private Breathing() {
        super(Sounds.BREATHING.get(), SoundSource.AMBIENT, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.8F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
    }

    static void start() {
        Minecraft minecraft = Minecraft.getInstance();
        if (current != null && !current.isStopped()) return;
        current = new Breathing();
        minecraft.getSoundManager().play(current);
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().screen instanceof InterrogationScreen) {
            awayTicks = 0;
        } else if (++awayTicks > GRACE) {
            stop();
        }
    }
}
