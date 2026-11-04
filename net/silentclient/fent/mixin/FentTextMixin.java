package net.silentclient.fent.mixin;

import net.silentclient.fent.FentAero;
import net.silentclient.fent.FentGlass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "net.silentclient.internal.K.g")
public abstract class FentTextMixin {
   @ModifyVariable(
      method = "b(Lnet/silentclient/internal/K/f;Lnet/silentclient/common/api/bridge/RenderBridge;Ljava/lang/String;FILjava/lang/String;Ljava/lang/String;F)V",
      at = @At("HEAD"),
      index = 4,
      argsOnly = true
   )
   private static int fent$whiteText(int colour) {
      if (!FentAero.glass() || FentAero.light()) {
         return colour;
      }
      int white = FentGlass.whiteFor(colour);
      return white == 0 ? colour : white;
   }
}
