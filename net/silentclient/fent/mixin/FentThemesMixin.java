package net.silentclient.fent.mixin;

import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.aA.a")
public abstract class FentThemesMixin {
   @Inject(method = "g()Z", at = @At("HEAD"), cancellable = true)
   private static void fent$themesForAll(CallbackInfoReturnable<Boolean> cir) {
      if (FentUnlocks.themes()) {
         cir.setReturnValue(Boolean.TRUE);
      }
   }
}
