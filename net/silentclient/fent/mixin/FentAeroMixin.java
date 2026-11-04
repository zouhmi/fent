package net.silentclient.fent.mixin;

import net.silentclient.common.api.setting.ColorSetting;
import net.silentclient.fent.FentAero;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentclient.internal.aA.a")
public abstract class FentAeroMixin {
   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$registerAero(CallbackInfo ci) {
      fent$tryRefresh();
   }

   @Inject(method = "o()V", at = @At("TAIL"))
   private static void fent$refreshAero(CallbackInfo ci) {
      fent$tryRefresh();
   }

   /**
    * theme colors get wired to real values only after the class initializes, so
    * the earliest injections see null data and skip; e()V then registers.
    */
   @Inject(method = "e()V", at = @At("TAIL"))
   private static void fent$refreshAeroOnColors(CallbackInfo ci) {
      fent$tryRefresh();
   }

   @Unique
   private static void fent$tryRefresh() {
      try {
         ColorSetting base = net.silentclient.internal.aA.a.a();
         ColorSetting accent = net.silentclient.internal.aA.a.b();
         if (base == null || accent == null || base.getValue() == null || accent.getValue() == null) {
            // Not wired up yet: skip silently; FentAero.ensureRegistered() retries later.
            return;
         }
         FentAero.refresh(net.silentclient.internal.aA.a.c(), net.silentclient.internal.aA.a.d());
      } catch (Throwable ignored) {
      }
   }
}
