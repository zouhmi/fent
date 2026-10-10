package net.silentclient.fent.mixin;

import net.silentclient.fent.FentAero;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// net.silentclient.internal.av.a is the theme system. Its static block builds the built in themes
// and then calls o(), which is also what rebuilds the custom theme whenever the Base and Accent
// colours are applied, so o() is the one place to hang Aero off: registered at the end of the
// static block, and rebuilt from the current colours every time the client rebuilds its own.
@Mixin(targets = "net.silentclient.internal.av.a")
public abstract class FentAeroMixin {
   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$registerAero(CallbackInfo ci) {
      FentAero.refresh(net.silentclient.internal.av.a.c(), net.silentclient.internal.av.a.d());
   }

   @Inject(method = "o()V", at = @At("TAIL"))
   private static void fent$refreshAero(CallbackInfo ci) {
      FentAero.refresh(net.silentclient.internal.av.a.c(), net.silentclient.internal.av.a.d());
   }
}
