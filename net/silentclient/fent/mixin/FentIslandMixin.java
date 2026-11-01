package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.silentclient.internal.W.i")
public abstract class FentIslandMixin {
   @Redirect(
      method = "a(Lnet/silentclient/common/api/bridge/RenderBridge;FFFFFFLjava/lang/String;Ljava/lang/String;)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$albumBox(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      FentGlass.bar(renderBridge, x, y, w, h, radius, color);
   }

   @Redirect(
      method = "c(Lnet/silentclient/common/api/bridge/RenderBridge;FFFF)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$eqBar(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      FentGlass.bar(renderBridge, x, y, w, h, radius, color);
   }

   @Redirect(
      method = "a(Lnet/silentclient/common/api/bridge/RenderBridge;FFFFFI)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRoundedRect(FFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$spectrumBar(RenderBridge renderBridge, float x, float y, float w, float h, float radius, int color) {
      FentGlass.bar(renderBridge, x, y, w, h, radius, color);
   }

   @Redirect(
      method = "a(Lnet/silentclient/common/api/context/ModContext;Lnet/silentclient/common/api/bridge/RenderBridge;FFFFFFFI[Ljava/lang/String;[I[Ljava/lang/String;[F[IIILjava/lang/String;F)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRgbaImage(Ljava/lang/String;[IIIFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$islandLogo(RenderBridge renderBridge, String key, int[] pixels, int width, int height,
                                float x, float y, float iw, float ih, int tint) {
      FentGlass.image(renderBridge, key, pixels, width, height, x, y, iw, ih, tint);
   }

   @Redirect(
      method = "a(Lnet/silentclient/common/api/context/ModContext;Lnet/silentclient/common/api/bridge/RenderBridge;FFFFFFFI[Ljava/lang/String;[I[Ljava/lang/String;[F[IIILjava/lang/String;F)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRgbaImage(Ljava/lang/String;[IIIFFFFI)V",
         ordinal = 1
      ),
      require = 0
   )
   private void fent$islandMark(RenderBridge renderBridge, String key, int[] pixels, int width, int height,
                                float x, float y, float iw, float ih, int tint) {
      FentGlass.image(renderBridge, key, pixels, width, height, x, y, iw, ih, tint);
   }

   @Redirect(
      method = "a(Lnet/silentclient/common/api/bridge/RenderBridge;Ljava/lang/String;FFFI)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/bridge/RenderBridge;drawRgbaImage(Ljava/lang/String;[IIIFFFFI)V",
         ordinal = 0
      ),
      require = 0
   )
   private void fent$slotIcon(RenderBridge renderBridge, String key, int[] pixels, int width, int height,
                              float x, float y, float iw, float ih, int tint) {
      FentGlass.image(renderBridge, key, pixels, width, height, x, y, iw, ih, tint);
   }
}
