package net.silentclient.fent.mixin;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.silentclient.common.api.services.emotes.Emote;
import net.silentclient.common.api.services.emotes.EmotesService;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Emotes live in net.silentclient.internal.ar.c.
// Owned ids sit in the field k, loaded from EmotesService.owned().
// Everything else in the menu reads that set, so we hand it the whole catalog.
@Mixin(targets = "net.silentclient.internal.ar.c")
public abstract class FentEmotesMixin {
   // d() refreshes the catalog and the owned set.
   @Redirect(
      method = "d()V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/silentclient/common/api/services/emotes/EmotesService;owned()Ljava/util/concurrent/CompletableFuture;"
      )
   )
   private static CompletableFuture<Set<String>> fent$ownAllEmotes(EmotesService service) {
      if (!FentUnlocks.emotes()) {
         return service.owned();
      }
      return service.catalog().thenApply(list -> {
         Set<String> ids = new HashSet<String>();
         if (list != null) {
            for (Emote emote : list) {
               if (emote != null && emote.getId() != null) {
                  ids.add(emote.getId());
               }
            }
         }
         // Empty catalog: fall back to the account's own emotes.
         return ids.isEmpty() ? null : ids;
      });
   }

   // j(String) is the "do I own this emote" check used by the play action.
   @Inject(method = "j(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
   private static void fent$ownsEverything(String id, CallbackInfoReturnable<Boolean> cir) {
      if (id != null && FentUnlocks.emotes()) {
         cir.setReturnValue(Boolean.TRUE);
      }
   }
}
