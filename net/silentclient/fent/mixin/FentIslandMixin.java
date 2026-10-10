package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentAero;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// The Dynamic Widget island paints its own plate instead of calling the shared HUD painter - it is
// the one widget whose background is a mode: two square corners for Notch, a pill otherwise - so it
// needs its own hook. These are the first rounded rectangle of each kind in onRender: the island's
// plate, and nothing before it.
//
// The overlay is added after the plate, with the same corners the plate was drawn with, so the
// notch keeps its flat top and the pill keeps both ends round.
@Mixin(targets = "net.silentclient.internal.V.i")
public abstract class FentIslandMixin {
   @Redirect(
      method = "onRender(Lnet/silentclient/common/api/context/ModContext;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$islandPlate(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      renderBridge.drawRoundedRect(x, y, w, h, radius, color);
      fent$glass(renderBridge, x, y, w, h, radius, radius, radius, radius, color);
   }

   @Redirect(
      method = "onRender(Lnet/silentclient/common/api/context/ModContext;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRectCorners(FFFFFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$islandPlateNotch(RenderBridge renderBridge, float x, float y, float w, float h,
                                      float r0, float r1, float r2, float r3, int color) {
      renderBridge.drawRoundedRectCorners(x, y, w, h, r0, r1, r2, r3, color);
      fent$glass(renderBridge, x, y, w, h, r0, r1, r2, r3, color);
   }

   private static void fent$glass(RenderBridge renderBridge, float x, float y, float w, float h,
                                  float r0, float r1, float r2, float r3, int color) {
      if (!FentAero.hudGlass()) {
         return;
      }
      FentGlass.hud(renderBridge, x, y, w, h, r0, r1, r2, r3, color);
   }
}
