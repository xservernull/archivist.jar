// hides sky, clouds, sun and moon in the old game dimensions using OldLook
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.client.shader.OldLook;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class OldSkyMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$noSky(CallbackInfo ci) {
        OldLook old = OldLook.current();
        if (old != null && old.flatSky()) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$noClouds(CallbackInfo ci) {
        OldLook old = OldLook.current();
        if (old != null && old.flatSky()) ci.cancel();
    }

    @ModifyArg(method = "renderSky",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"),
            index = 1, require = 0)
    private ResourceLocation archivist$noSunMoon(ResourceLocation original) {
        return OldLook.current() != null ? OldLook.BLANK : original;
    }
}
