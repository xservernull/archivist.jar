// cancels every particle in the quiet place and the old game dimensions
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.Archivist;
import com.xservernull.archivist.client.shader.OldLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class QuietPlaceParticleMixin {
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$noParticlesInQuietPlace(Particle particle, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && (level.dimension().equals(Archivist.QUIET_PLACE)
                || OldLook.get(level.dimension()) != null)) {
            ci.cancel();
        }
    }
}
