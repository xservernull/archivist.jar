// freezes the shader game time in the quiet place right after vanilla sets it
package com.xservernull.archivist.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class QuietPlaceWindMixin {
    @Inject(method = "renderLevel",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderGameTime(JF)V",
                    shift = At.Shift.AFTER),
            require = 0)
    private void archivist$freezeShaderTime(CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension().equals(Archivist.QUIET_PLACE)) {
            RenderSystem.setShaderGameTime(0L, 0.0F);
        }
    }
}
