package net.silentclient.fent.mixin;

import net.silentclient.common.api.context.ModContext;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// The colour editor never opens from a colour setting.
//
// net.silentclient.internal.ai.n is the menu controller and b(ModContext, action, target) is where
// a click is dispatched. Two actions open the editor: "mod-setting-color-editor", which the colour
// swatch on every setting row sends (Settings, Themes, the Premium tab, a mod's own page), and
// "config-accent-editor" from the config screen. Both are cancelled here, so the swatch keeps
// showing the colour and nothing opens - the editor's state is only ever written from these two
// actions, which is also what keeps the inline editor on a mod's settings panel and the floating one
// on the mods screen closed, because both are drawn off that same state.
//
// The action is the method's first String; the second is the setting key the swatch passed, which is
// left alone, so nothing else on the screen changes behaviour.
@Mixin(targets = "net.silentclient.internal.ai.n")
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
