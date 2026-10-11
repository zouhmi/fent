package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Mouse Strokes paints its own background: one cell per position in the trail, a square or rounded
// rectangle with the module's own background colour, which is the whole body of the widget. Like
// Keystrokes it never reaches the shared HUD painter, so it stayed flat under Aero.
//
// The two rectangles in onRender are the cell, in its rounded and its square shape. Both go to the
// tile material, so a trail of cells reads as a lit sheet of glass instead of a row of flat squares.
// The dot on top is drawn by another method and is left alone.
@Mixin(targets = "net.silentclient.internal.V.w")
public abstract class FentStrokesMixin {
   @Redirect(
      method = "onRender(Lnet/silentclient/common/api/context/ModContext;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private void fent$cellTile(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      FentGlass.tile(renderBridge, x, y, w, h, radius, color);
   }

   @Redirect(
      method = "onRender(Lnet/silentclient/common/api/context/ModContext;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRect(FFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private void fent$cellTileStraight(RenderBridge renderBridge, float x, float y, float w, float h, int color) {
      FentGlass.tile(renderBridge, x, y, w, h, 0.0f, color);
   }
}
