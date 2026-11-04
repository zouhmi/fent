package net.silentclient.fent.mixin;

import net.silentclient.fent.FentAero;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.aA.a")
public abstract class FentTokensMixin {
   @Inject(method = "a(Ljava/lang/String;I)I", at = @At("RETURN"), cancellable = true)
   private static void fent$whiteText(String token, int fallback, CallbackInfoReturnable<Integer> cir) {
      // If the Liquid Glass theme could not be registered during static init
      // (TEST3.1 has null theme colors while the class is loading), retry here:
      // this lookup runs constantly once the UI is up.
      FentAero.ensureRegistered();
      if (!FentAero.active() || FentAero.light()) {
         return;
      }
      int white = FentGlass.whiteForToken(token, cir.getReturnValueI());
      if (white != 0) {
         cir.setReturnValue(Integer.valueOf(white));
      }
   }
}
