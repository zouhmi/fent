package net.silentclient.fent.mixin;

import java.util.Locale;
import java.util.Map;
import net.silentclient.common.api.services.cosmetics.Cosmetic;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.fent.FentUnlocks;
import net.silentclient.internal.ai.r;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Cosmetics live in net.silentclient.internal.ar.b.
// b(Cosmetic) is the owned check the menu asks.
// a(CosmeticType, String) is the equip action, the only caller is the menu.
// b(type, id) records the pick, f(type, id) publishes it, the server call is skipped.
// Picks are saved under setting.* so SilentClient writes them to config.json.
@Mixin(targets = "net.silentclient.internal.ar.b")
public abstract class FentCosmeticTypeMixin {
   @Unique
   private static final String FENT_PREFIX = "setting.fent.selected.";

   @Shadow
   public abstract boolean a(CosmeticType type);

   @Shadow
   public abstract long b(CosmeticType type, String id);

   @Shadow
   protected abstract void f(CosmeticType type, String id);

   // Owned check: treat every catalog entry as owned.
   @Inject(
      method = "b(Lnet/silentclient/common/api/services/cosmetics/Cosmetic;)Z",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$unlockAll(Cosmetic cosmetic, CallbackInfoReturnable<Boolean> cir) {
      if (cosmetic != null && FentUnlocks.cosmetics()) {
         cir.setReturnValue(Boolean.TRUE);
      }
   }

   // Equip without the server round trip, which would answer 403 and revert.
   @Inject(
      method = "a(Lnet/silentclient/common/api/services/cosmetics/CosmeticType;Ljava/lang/String;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$equipLocally(CosmeticType type, String id, CallbackInfo ci) {
      if (type == null || this.a(type) || !FentUnlocks.cosmetics()) {
         // Client-owned types apply through am.e, leave that path alone.
         return;
      }
      String value = id == null ? "" : id;
      this.b(type, value);
      this.f(type, value);
      fent$save(type, value);
      ci.cancel();
   }

   // Server expiry table: an unlock does not expire, and it drives the temporary badge.
   @Inject(method = "a(Ljava/util/Map;)V", at = @At("HEAD"), cancellable = true)
   private void fent$ignoreExpiries(Map<String, Map<String, Long>> expiries, CallbackInfo ci) {
      if (FentUnlocks.cosmetics()) {
         ci.cancel();
      }
   }

   // Merge of server and outfit state, which drops local picks, so re-apply after it.
   @Inject(method = "a(Ljava/util/Map;J)V", at = @At("RETURN"))
   private void fent$reapplySaved(Map<CosmeticType, String> equipped, long generation, CallbackInfo ci) {
      fent$applySaved();
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   private void fent$restoreSelections(CallbackInfo ci) {
      fent$applySaved();
   }

   @Unique
   private void fent$applySaved() {
      if (!FentUnlocks.cosmetics()) {
         return;
      }
      for (CosmeticType type : CosmeticType.values()) {
         if (this.a(type)) {
            continue;
         }
         String saved = r.d(fent$key(type));
         if (saved.isEmpty()) {
            continue;
         }
         this.b(type, saved);
      }
   }

   // Unequip forgets the pick.
   @Unique
   private static void fent$save(CosmeticType type, String id) {
      r.a(fent$key(type), id.isEmpty() ? null : id);
   }

   @Unique
   private static String fent$key(CosmeticType type) {
      return FENT_PREFIX + type.name().toLowerCase(Locale.ROOT);
   }
}
