package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.MinecraftBridge;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.common.api.setting.ModeSetting;
import net.silentclient.fent.FentWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.V.i")
public abstract class FentWidgetMixin {
   @Shadow
   @Final
   @Mutable
   private static String[] e;

   @Shadow
   @Final
   private static String c;

   @Shadow
   @Final
   private ModeSetting k;

   @Shadow
   @Final
   private ModeSetting l;

   @Shadow
   @Final
   private ModeSetting m;

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$slots(CallbackInfo ci) {
      e = new String[] {
         "None", "FPS", "Ping", "Time", "Date", "Day", "Biome", "Players", "Version", "Server IP",
         "Track", "Artist", "Song Time", "Current Lyric"
      };
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void fent$offsetSetting(CallbackInfo ci) {
      FentWidget.registerOffset(this);
      FentWidget.expandModes(k, l, m);
   }

   @Inject(
      method = "a(Ljava/lang/String;Lnet/silentclient/common/api/bridge/MinecraftBridge;Lnet/silentclient/common/api/bridge/RenderBridge;)V",
      at = @At("TAIL")
   )
   private void fent$slot(String mode, MinecraftBridge minecraftBridge, RenderBridge renderBridge, CallbackInfo ci) {
      FentWidget.draw(this, mode, minecraftBridge, renderBridge, c);
   }

   @Inject(method = "W()F", at = @At("RETURN"), cancellable = true)
   private void fent$lyricsOffset(CallbackInfoReturnable<Float> cir) {
      double offset = FentWidget.offset();
      if (offset != 0.0) {
         cir.setReturnValue(Float.valueOf((float)(cir.getReturnValueF() + offset)));
      }
   }
}
