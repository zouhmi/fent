package net.silentclient.fent.mixin;

import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Themes live in net.silentclient.internal.av.a.
// g() is the only premium check there: it gates the custom theme and the colour rows
// that D.p hides behind it. Normal themes are local lists and need nothing.
@Mixin(targets = "net.silentclient.internal.av.a")
public abstract class FentThemesMixin {
   @Inject(method = "g()Z", at = @At("HEAD"), cancellable = true)
   private static void fent$themesForAll(CallbackInfoReturnable<Boolean> cir) {
      if (FentUnlocks.themes()) {
         cir.setReturnValue(Boolean.TRUE);
      }
   }
}
