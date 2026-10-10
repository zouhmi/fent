package net.silentclient.fent.mixin;

import net.silentclient.common.api.context.ModContext;
import net.silentclient.fent.FentAero;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// net.silentclient.internal.V.c is the base every HUD module shares, and a() is the plate painter
// its widgets call before they draw their contents: the module's own background colour, rounded by
// the module's own radius, at the module's own position and scale.
//
// The glass goes on after that plate, so the widget keeps the colour and alpha the user configured
// and gains the lit top edge and the hairline rim, which is what makes it read as glass over the
// world. A module that fades in or out brings its strength with it, because the overlay follows the
// plate's alpha.
@Mixin(targets = "net.silentclient.internal.V.c")
public abstract class FentHudMixin {
   @Shadow
   protected static boolean g;

   @Shadow
   public abstract float M();

   @Shadow
   public abstract float N();

   @Shadow
   public abstract float D();

   @Shadow
   protected abstract float O();

   @Shadow
   protected abstract int b(int fallback);

   @Inject(
      method = "a(Lnet/silentclient/common/api/context/ModContext;FFI)V",
      at = @At("RETURN")
   )
   private void fent$hudGlass(ModContext context, float width, float height, int fallback, CallbackInfo ci) {
      if (g || !FentAero.hudGlass() || context == null) {
         return;
      }
      int plate = this.b(fallback);
      int alpha = plate >>> 24 & 0xFF;
      if (alpha == 0) {
         return;
      }
      float scale = this.D();
      float w = width * scale;
      float h = height * scale;
      float radius = Math.min(this.O() * scale, Math.min(w, h) / 2.0f);
      FentGlass.hud(context.render(), this.M(), this.N(), w, h, radius, radius, radius, radius, plate);
   }
}
