package net.silentclient.fent.mixin;

import net.silentclient.common.api.context.ModContext;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.an.a")
public abstract class FentCoinsMixin {
   @Inject(method = "b(Lnet/silentclient/common/api/context/ModContext;)Z", at = @At("HEAD"), cancellable = true)
   private static void fent$coinsGate(ModContext modContext, CallbackInfoReturnable<Boolean> cir) {
      if (FentUnlocks.coinsOff()) {
         cir.setReturnValue(Boolean.FALSE);
      }
   }
}
