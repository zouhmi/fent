package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
