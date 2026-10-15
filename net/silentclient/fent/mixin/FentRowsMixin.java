package net.silentclient.fent.mixin;

import net.silentclient.common.api.setting.Setting;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.K.i")
public abstract class FentRowsMixin {
   @Inject(method = "y(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private static void fent$bareFallbackRow(String key, CallbackInfoReturnable<String> cir) {
      fent$bare(key, cir);
   }

   @Inject(
      method = "a(Lnet/silentclient/common/api/setting/Setting;Ljava/lang/String;)Ljava/lang/String;",
      at = @At("HEAD"),
      cancellable = true
   )
   private static void fent$bareSettingRow(Setting<?> setting, String key, CallbackInfoReturnable<String> cir) {
      fent$bare(key, cir);
   }

   private static void fent$bare(String key, CallbackInfoReturnable<String> cir) {
      if (!FentUnlocks.transparentRows() || !"rowBg".equals(key)) {
         return;
      }
      cir.setReturnValue("#00000000");
   }
}
