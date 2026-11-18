// swaps the rain texture for blood rain while the client is in never forget you
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.Archivist;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LevelRenderer.class)
public class BloodRainMixin {
    @ModifyArg(method = "renderSnowAndRain",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"),
            index = 1, require = 0)
    private ResourceLocation archivist$bloodRain(ResourceLocation original) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(Archivist.NEVER_FORGET_YOU)) return original;
        return "textures/environment/rain.png".equals(original.getPath()) ? Archivist.BLOOD_RAIN : original;
    }
}
