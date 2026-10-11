package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// Keystrokes draws its own keys: every key box is a rounded or square rectangle painted straight onto
// the bridge, with the module's own background colour, and the widget has no plate of its own. It is
// therefore one of the modules the shared HUD painter never reaches, and it used to stay flat while
// the rest of the HUD was glass.
//
// The key box painter is the one method all of those rectangles go through, and it has exactly two
// shapes - rounded and square - so both call sites are taken over and handed to the tile material: a
// softer plate, the gradient, and the hairline. Nothing about the keys, their layout, their click
// handling or their text changes; only how the box behind each one is filled.
@Mixin(targets = "net.silentclient.internal.V.t")
public abstract class FentKeysMixin {
   @Redirect(
      method = "a(Lnet/silentclient/common/api/bridge/RenderBridge;FFFFFI)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private static void fent$keyTile(RenderBridge renderBridge, float x, float y, float w, float h,
                                    float radius, int color) {
      FentGlass.tile(renderBridge, x, y, w, h, radius, color);
   }

   @Redirect(
      method = "a(Lnet/silentclient/common/api/bridge/RenderBridge;FFFFFI)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRect(FFFFI)V",
         ordinal = 0
      ),
      require = 1
   )
   private static void fent$keyTileStraight(RenderBridge renderBridge, float x, float y, float w, float h, int color) {
      FentGlass.tile(renderBridge, x, y, w, h, 0.0f, color);
   }
}
