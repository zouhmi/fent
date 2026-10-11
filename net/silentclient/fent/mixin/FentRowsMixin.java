package net.silentclient.fent.mixin;

import net.silentclient.common.api.setting.Setting;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// A settings row is drawn with a plate behind it, the "rowBg" the setting-row template asks for.
//
// net.silentclient.internal.K.i answers it in two places. The setting rows themselves - Settings,
// Themes, the Premium tab, a mod's settings page - resolve it in a(Setting, key), and every other
// row provider in the menu - the Modules and Unlocker pages, whose rows are D.p$b records, the mod
// list, the changelog - calls into the shared row chrome in y(key) for the keys it does not answer
// itself; that is where their padding, rounding and background come from. The client already has a
// value for a row with no plate, the fully transparent "#00000000" it uses for some of its own rows,
// so both keys are answered with it here.
//
// Only the background goes: the row keeps its hairline border and its controls, which is what keeps
// a list readable without an opaque plate under every line. The setting-row template has no hover
// background of its own, so nothing flashes back in under the pointer.
@Mixin(targets = "net.silentclient.internal.K.i")
public abstract class FentRowsMixin {
   // The shared row chrome every row provider falls back to.
   @Inject(method = "y(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
   private static void fent$bareFallbackRow(String key, CallbackInfoReturnable<String> cir) {
      fent$bare(key, cir);
   }

   // The setting rows that answer the key themselves.
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
