package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// net.silentclient.internal.V.c is the base every HUD module shares, and a(ModContext, width, height,
// fallback) is the plate painter its widgets call before they draw their contents: the module's own
// background colour, rounded by the module's own radius, at the module's own position and scale.
//
// The plate itself is what the user configured, so this does not replace the call - it takes it over.
// Aero draws the same rectangle, with the alpha lowered so the world shows through, and, where a real
// blurred backdrop could be sampled from the framebuffer, with that behind it. The lit top edge and
// the hairline go on afterwards. A module that is not under Aero, or is being drawn into the modules
// menu preview, gets its own call straight back.
@Mixin(targets = "net.silentclient.internal.V.c")
public abstract class FentHudMixin {
   @Redirect(
      method = "a(Lnet/silentclient/common/api/context/ModContext;FFI)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private void fent$plate(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      FentGlass.plate(renderBridge, x, y, w, h, radius, radius, radius, radius, color);
   }

   @Redirect(
      method = "a(Lnet/silentclient/common/api/context/ModContext;FFI)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRect(FFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private void fent$plateStraight(RenderBridge renderBridge, float x, float y, float w, float h, int color) {
      FentGlass.plate(renderBridge, x, y, w, h, 0.0f, 0.0f, 0.0f, 0.0f, color);
   }
}
