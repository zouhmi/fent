package net.silentclient.fent.mixin;

import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.D.h")
public abstract class FentCoinsMixin {
   @Inject(method = "u()Z", at = @At("HEAD"), cancellable = true)
   private static void fent$coinsGate(CallbackInfoReturnable<Boolean> cir) {
      if (FentUnlocks.coinsOff()) {
         cir.setReturnValue(Boolean.FALSE);
      }
   }
}
