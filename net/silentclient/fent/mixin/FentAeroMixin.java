package net.silentclient.fent.mixin;

import net.silentclient.fent.FentAero;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentclient.internal.aA.a")
public abstract class FentAeroMixin {
   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$registerAero(CallbackInfo ci) {
      FentAero.refresh(net.silentclient.internal.aA.a.c(), net.silentclient.internal.aA.a.d());
   }

   @Inject(method = "o()V", at = @At("TAIL"))
   private static void fent$refreshAero(CallbackInfo ci) {
      FentAero.refresh(net.silentclient.internal.aA.a.c(), net.silentclient.internal.aA.a.d());
   }
}
