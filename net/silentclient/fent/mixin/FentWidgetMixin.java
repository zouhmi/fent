package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.MinecraftBridge;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The Dynamic Widget (net.silentclient.internal.V.i): three info slots, synced lyrics under the
// track, and no way to nudge the lyrics when a player's progress runs ahead or behind the words.
//
// The slot list it ships offers None, FPS, Ping, Time and Server IP, but the widget can already
// draw a Version slot that the list never offers, and the bridge carries a date, the world day
// count, the biome and the online player count that nothing asks it for.
@Mixin(targets = "net.silentclient.internal.V.i")
public abstract class FentWidgetMixin {
   // The shipped list plus the slots the widget can already draw and the ones the bridge already
   // carries: Version was reachable nowhere, Date, Day, Biome and Players nowhere at all.
   @Shadow
   @Final
   @Mutable
   private static String[] e;

   @Shadow
   @Final
   private static String c;

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$slots(CallbackInfo ci) {
      e = new String[] {
         "None", "FPS", "Ping", "Time", "Date", "Day", "Biome", "Players", "Version", "Server IP"
      };
   }

   @Inject(method = "<init>", at = @At("TAIL"))
   private void fent$offsetSetting(CallbackInfo ci) {
      FentWidget.registerOffset(this);
   }

   // The shipped switch answers the known slots and returns for the rest, so the extra ones are
   // drawn here, after it, through the widget's own slot drawing.
   @Inject(
      method = "a(Ljava/lang/String;Lnet/silentclient/common/api/bridge/MinecraftBridge;Lnet/silentclient/common/api/bridge/RenderBridge;)V",
      at = @At("TAIL")
   )
   private void fent$slot(String mode, MinecraftBridge minecraftBridge, RenderBridge renderBridge, CallbackInfo ci) {
      FentWidget.draw(this, mode, minecraftBridge, renderBridge, c);
   }

   // W() is the progress the lyrics are timed against. The offset moves that clock, so a line that
   // lands late or early can be brought back without touching the player.
   @Inject(method = "W()F", at = @At("RETURN"), cancellable = true)
   private void fent$lyricsOffset(CallbackInfoReturnable<Float> cir) {
      double offset = FentWidget.offset();
      if (offset != 0.0) {
         cir.setReturnValue(Float.valueOf((float)(cir.getReturnValueF() + offset)));
      }
   }
}
