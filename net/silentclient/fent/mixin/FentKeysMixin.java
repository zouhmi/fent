package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.silentclient.internal.W.t")
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
