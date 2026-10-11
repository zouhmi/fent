package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The Dynamic Widget island paints its own plate instead of calling the shared HUD painter - it is the
// one widget whose background is a mode: two square corners for Notch, a pill otherwise - so its two
// first rounded rectangles in onRender are taken over here. Both go through the same material every
// other widget uses, which is also what carries the framebuffer refraction: the widget is the one the
// blurred backdrop is worth most on, because it is the largest surface a player looks at in game.
@Mixin(targets = "net.silentclient.internal.V.i")
public abstract class FentIslandMixin {
   @Redirect(
      method = "onRender(Lnet/silentclient/common/api/context/ModContext;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private void fent$islandPlate(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      FentGlass.plate(renderBridge, x, y, w, h, radius, radius, radius, radius, color);
   }

   @Redirect(
      method = "onRender(Lnet/silentclient/common/api/context/ModContext;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRectCorners(FFFFFFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private void fent$islandPlateNotch(RenderBridge renderBridge, float x, float y, float w, float h,
                                      float r0, float r1, float r2, float r3, int color) {
      FentGlass.plate(renderBridge, x, y, w, h, r0, r1, r2, r3, color);
   }
}
