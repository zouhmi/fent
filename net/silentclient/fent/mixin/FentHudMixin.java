package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
