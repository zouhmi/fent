package net.silentclient.fent.mixin;

import net.silentclient.common.api.context.ModContext;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Coins tracker (net.silentclient.internal.am.a). Premium is what turns it on, so an
// unlocked account starts reporting active minutes to /payments/pay/coins/activity.
// b(ModContext) is the premium gate it consults every few seconds; the gate answers
// false while the Keep Coins Off row is set, and the tracker stops before any work.
@Mixin(targets = "net.silentclient.internal.am.a")
public abstract class FentCoinsMixin {
   @Inject(method = "b(Lnet/silentclient/common/api/context/ModContext;)Z", at = @At("HEAD"), cancellable = true)
   private static void fent$coinsGate(ModContext modContext, CallbackInfoReturnable<Boolean> cir) {
      if (FentUnlocks.coinsOff()) {
         cir.setReturnValue(Boolean.FALSE);
      }
   }
}
