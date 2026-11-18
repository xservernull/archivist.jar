// swaps minecraft sounds for archivist:shoto while the language is mandanga
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.client.Mandanga;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundEngine.class)
public class MandangaSoundMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$shoto(SoundInstance sound, CallbackInfo ci) {
        if (!Mandanga.active() || !Mandanga.shouldSwap(sound)) return;
        ci.cancel();
        ((SoundEngine) (Object) this).play(Mandanga.shoto(sound));
    }
}
