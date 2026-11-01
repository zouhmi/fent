package net.silentclient.fent.mixin;

import net.silentclient.common.api.context.ModContext;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentclient.internal.am.q")
public abstract class FentColorPickerMixin {
   @Inject(
      method = "b(Lnet/silentclient/common/api/context/ModContext;Ljava/lang/String;Ljava/lang/String;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$noColorPicker(ModContext modContext, String action, String target, CallbackInfo ci) {
      if (action == null || !FentUnlocks.hideColorPicker()) {
         return;
      }
      if ("mod-setting-color-editor".equals(action) || "config-accent-editor".equals(action)) {
         ci.cancel();
      }
   }
}
