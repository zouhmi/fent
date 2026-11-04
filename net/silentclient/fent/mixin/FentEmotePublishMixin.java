package net.silentclient.fent.mixin;

import net.silentclient.fent.FentShare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes OUR emote starts/stops to the shared sync API. The target is the
 * production EmotesService (signed-in SilentClient accounts); its
 * a(String,String,long) sets the local player's own state after a successful
 * play, and a(String) clears it after a stop. State keys other players' events
 * are handled elsewhere (F records), so these hooks only see our own actions.
 */
@Mixin(targets = "net.silentclient.internal.aq.I$e")
public abstract class FentEmotePublishMixin {
   @Inject(method = "a(Ljava/lang/String;Ljava/lang/String;J)V", at = @At("TAIL"))
   private void fent$publishStart(String uid, String emoteId, long startedAt, CallbackInfo ci) {
      FentShare.publishLocalEmote(uid, emoteId, startedAt);
   }

   @Inject(method = "a(Ljava/lang/String;)V", at = @At("TAIL"))
   private void fent$publishStop(String uid, CallbackInfo ci) {
      FentShare.publishLocalEmoteStop(uid);
   }
}
