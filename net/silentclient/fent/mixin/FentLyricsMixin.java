package net.silentclient.fent.mixin;

import net.silentclient.fent.FentLyrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// e(String) is the one HTTP GET the lyrics module makes: the exact match against lrclib and, when
// that misses, its search. A track already answered before is served from disk here instead, so the
// request never leaves the machine, and every answer that arrives is kept on the way out.
@Mixin(targets = "net.silentclient.internal.ax.l")
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
      FentLyrics.write(url, cir.getReturnValue());
   }
}
