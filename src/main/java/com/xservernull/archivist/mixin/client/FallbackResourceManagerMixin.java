// wraps textures from the resource manager with the mandanga faces
// only does anything while the mandanga language is on
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.client.Mandanga;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

@Mixin(FallbackResourceManager.class)
public class FallbackResourceManagerMixin {
    @Inject(method = "listResources", at = @At("RETURN"), cancellable = true, require = 0)
    private void archivist$mandangaList(String path, Predicate<ResourceLocation> filter,
                                        CallbackInfoReturnable<Map<ResourceLocation, Resource>> cir) {
        if (!path.startsWith("textures") || !Mandanga.active()) return;
        Map<ResourceLocation, Resource> out = new TreeMap<>();
        cir.getReturnValue().forEach((id, res) -> out.put(id, Mandanga.wrap(id, res)));
        cir.setReturnValue(out);
    }

    @Inject(method = "getResource", at = @At("RETURN"), cancellable = true, require = 0)
    private void archivist$mandangaGet(ResourceLocation id, CallbackInfoReturnable<Optional<Resource>> cir) {
        if (!id.getPath().startsWith("textures") || !Mandanga.active()) return;
        cir.setReturnValue(cir.getReturnValue().map(r -> Mandanga.wrap(id, r)));
    }
}
