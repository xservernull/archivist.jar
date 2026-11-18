// mutes vanilla ambient and music sounds in the quiet place, mod sounds still play
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class QuietPlaceSoundMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$muteVanillaAmbience(SoundInstance sound, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(Archivist.QUIET_PLACE)) return;

        SoundSource source = sound.getSource();
        if ((source == SoundSource.AMBIENT || source == SoundSource.MUSIC)
                && "minecraft".equals(sound.getLocation().getNamespace())) {
            ci.cancel();
        }
    }
}
