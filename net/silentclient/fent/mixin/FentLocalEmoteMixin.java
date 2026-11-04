package net.silentclient.fent.mixin;

import net.silentclient.common.api.services.emotes.EmotePlayState;
import net.silentclient.fent.FentShare;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Same as FentEmotePublishMixin but for the fallback EmotesService used when
 * no SilentClient account is signed in (local-only emote playback), so fent
 * sync also works without an account.
 */
@Mixin(targets = "net.silentclient.internal.aq.W")
public abstract class FentLocalEmoteMixin {
   @Inject(
      method = "a(Ljava/lang/String;Lnet/silentclient/common/api/services/emotes/EmotePlayState;)V",
      at = @At("TAIL")
   )
   private void fent$publishStart(String uuid, EmotePlayState state, CallbackInfo ci) {
      if (state != null && state.isPlaying()) {
         boolean infinite = false;
         try {
            infinite = FentShare.fent$isInfiniteState(state);
         } catch (Throwable ignored) {
         }
         FentShare.publishLocalEmote(state.getEmoteId(), state.getStartedAtMs(), infinite);
      }
   }

   @Inject(method = "a(Ljava/lang/String;)V", at = @At("TAIL"))
   private void fent$publishStop(String uuid, CallbackInfo ci) {
      FentShare.publishLocalEmoteStop();
   }
}
