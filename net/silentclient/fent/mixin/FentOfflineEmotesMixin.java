package net.silentclient.fent.mixin;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.silentclient.common.api.services.emotes.Emote;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Offline/builtin emote service bypass. Same deal as FentEmotePlayMixin but
 * for the fallback service: locked emotes play locally and owned() lists
 * the whole builtin catalog while the unlocker is on.
 */
@Mixin(targets = "net.silentclient.internal.aq.W")
public abstract class FentOfflineEmotesMixin {
   @Shadow
   private List<Emote> c;

   @Inject(
      method = "play(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
      at = @At("RETURN"),
      cancellable = true
   )
   private void fent$playLocal(String id, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
      if (id == null || id.isEmpty() || !FentUnlocks.emotes()) {
         return;
      }
      try {
         CompletableFuture<Boolean> future = cir.getReturnValue();
         if (future == null || future.getNow(null) != Boolean.FALSE) {
            return;
         }
      } catch (Throwable ignored) {
         return;
      }
      cir.setReturnValue(CompletableFuture.completedFuture(Boolean.TRUE));
   }

   @Inject(method = "owned()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
   private void fent$ownAll(CallbackInfoReturnable<CompletableFuture<Set<String>>> cir) {
      if (!FentUnlocks.emotes()) {
         return;
      }
      try {
         CompletableFuture<Set<String>> future = cir.getReturnValue();
         if (future == null) {
            return;
         }
         future.thenAccept(owned -> {
            try {
               if (owned == null) {
                  return;
               }
               List<Emote> catalog = this.c;
               if (catalog == null) {
                  return;
               }
               for (Emote emote : catalog) {
                  if (emote == null) {
                     continue;
                  }
                  String id;
                  try {
                     id = emote.getId();
                  } catch (Throwable ignored) {
                     continue;
                  }
                  if (id != null && !id.isEmpty()) {
                     owned.add(id);
                  }
               }
            } catch (Throwable ignored) {
            }
         });
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not unlock the offline emote list: " + throwable);
      }
   }
}