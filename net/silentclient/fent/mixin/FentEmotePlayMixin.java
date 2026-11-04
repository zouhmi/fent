package net.silentclient.fent.mixin;

import java.util.HashSet;
import java.util.Map;
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
 * Server-called emote gate bypass (network service). When the unlocker is on,
 * every emote behaves like a free one: the menus already show them as owned
 * (FentEmotesMixin) and play() resolves TRUE so the local animation runs.
 * Online TRUE results are untouched, so the broadcast to other players still
 * goes out exactly as before; only a server FALSE (locked / offline) is
 * upgraded, and offline there is nothing to broadcast to anyway.
 */
@Mixin(targets = "net.silentclient.internal.aq.I$e")
public abstract class FentEmotePlayMixin {
   @Shadow
   private volatile Map<String, Emote> f;

   @Inject(
      method = "play(Ljava/lang/String;)Ljava/util/concurrent/CompletableFuture;",
      at = @At("RETURN"),
      cancellable = true
   )
   private void fent$playLocal(String id, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
      fent$upgrade(id, cir);
   }

   @Inject(
      method = "play(Ljava/lang/String;Z)Ljava/util/concurrent/CompletableFuture;",
      at = @At("RETURN"),
      cancellable = true
   )
   private void fent$playLocalLoop(String id, boolean loop,
                                     CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
      fent$upgrade(id, cir);
   }

   private void fent$upgrade(String id, CallbackInfoReturnable<CompletableFuture<Boolean>> cir) {
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

   @Inject(method = "owned()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"), cancellable = true)
   private void fent$ownAll(CallbackInfoReturnable<CompletableFuture<Set<String>>> cir) {
      if (!FentUnlocks.emotes()) {
         return;
      }
      try {
         CompletableFuture<Set<String>> future = cir.getReturnValue();
         Map<String, Emote> catalog = this.f;
         if (future == null || catalog == null || catalog.isEmpty()) {
            return;
         }
         cir.setReturnValue(future.thenApply(owned -> {
            try {
               Set<String> grown = owned == null ? new HashSet<String>() : new HashSet<String>(owned);
               grown.addAll(catalog.keySet());
               return grown;
            } catch (Throwable ignored) {
               return owned;
            }
         }));
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not unlock the emote service list: " + throwable);
      }
   }
}