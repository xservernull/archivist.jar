// answers archivist and minecraft block/item translation keys from our own Lang
// so server sent text follows the language picked in the Archivist menu
package com.xservernull.archivist.mixin.client;

import com.xservernull.archivist.client.Lang;
import net.minecraft.client.resources.language.ClientLanguage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
    @Inject(method = "getOrDefault", at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$lang(String key, String fallback, CallbackInfoReturnable<String> cir) {
        if (!archivist$mine(key)) return;
        String text = Lang.lookup(key);
        if (text != null) cir.setReturnValue(text);
    }

    @Inject(method = "has", at = @At("HEAD"), cancellable = true, require = 0)
    private void archivist$has(String key, CallbackInfoReturnable<Boolean> cir) {
        if (archivist$mine(key) && Lang.lookup(key) != null) cir.setReturnValue(true);
    }

    private static boolean archivist$mine(String key) {
        return key != null && (key.startsWith("archivist.") || key.startsWith("block.minecraft.") || key.startsWith("item.minecraft."));
    }
}
