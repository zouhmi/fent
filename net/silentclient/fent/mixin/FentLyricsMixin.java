package net.silentclient.fent.mixin;

import net.silentclient.fent.FentLyrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.aC.k")
public abstract class FentLyricsMixin {
   @Inject(method = "e(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private static void fent$cachedLyrics(String url, CallbackInfoReturnable<String> cir) {
      String cached = FentLyrics.read(url);
      if (cached != null) {
         cir.setReturnValue(cached);
      }
   }

   @Inject(method = "e(Ljava/lang/String;)Ljava/lang/String;", at = @At("RETURN"))
   private static void fent$storeLyrics(String url, CallbackInfoReturnable<String> cir) {
      if (cir.isCancelled()) {
         return;
      }
      FentLyrics.write(url, cir.getReturnValue());
   }
}
