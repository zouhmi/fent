package net.silentclient.fent.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.silentclient.common.api.services.emotes.Emote;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.silentclient.fent.FentUnlocks;

@Mixin(targets = "net.silentclient.internal.at.c")
public abstract class FentEmotesMixin {
   @Shadow
   private volatile List<Emote> j;

   @Shadow
   private volatile Set<String> k;

   @Inject(method = "j(Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true)
   private void fent$ownsEverything(String id, CallbackInfoReturnable<Boolean> cir) {
      if (id != null && FentUnlocks.emotes()) {
         cir.setReturnValue(Boolean.TRUE);
      }
   }

   @Inject(method = "a(Ljava/util/Set;)V", at = @At("RETURN"))
   private void fent$seedOwnedFromServer(Set<String> owned, CallbackInfo ci) {
      fent$seedOwned();
   }

   @Inject(method = "a(Ljava/util/List;)V", at = @At("RETURN"))
   private void fent$seedOwnedFromCatalog(List<Emote> catalog, CallbackInfo ci) {
      fent$seedOwned();
   }

   private void fent$seedOwned() {
      if (!FentUnlocks.emotes()) {
         return;
      }
      try {
         List<Emote> catalog = this.j;
         Set<String> owned = this.k;
         if (catalog == null || catalog.isEmpty() || owned == null) {
            return;
         }
         List<String> missing = null;
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
            if (id == null || id.isEmpty() || owned.contains(id)) {
               continue;
            }
            if (missing == null) {
               missing = new ArrayList<String>();
            }
            missing.add(id);
         }
         if (missing != null && !missing.isEmpty()) {
            try {
               owned.addAll(missing);
            } catch (UnsupportedOperationException fixed) {
               java.util.Set<String> grown = new java.util.LinkedHashSet<String>(owned);
               grown.addAll(missing);
               this.k = grown;
            }
         }
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not unlock the emote list: " + throwable);
      }
   }
}
