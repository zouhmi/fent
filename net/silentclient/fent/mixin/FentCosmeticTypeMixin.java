package net.silentclient.fent.mixin;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.silentclient.common.api.services.cosmetics.Cosmetic;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.fent.FentUnlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.aw.d")
public abstract class FentCosmeticTypeMixin {
   @Unique
   private static final String FENT_PREFIX = "fent.selected.";

   @Shadow
   public abstract boolean a(CosmeticType type);

   @Shadow
   public abstract String b(CosmeticType type);

   @Shadow
   public abstract String d(CosmeticType type);

   @Shadow
   public abstract Cosmetic c(CosmeticType type, String id);

   @Shadow
   public abstract long b(CosmeticType type, String id);

   @Shadow
   private volatile Map<String, Set<String>> q;

   @Shadow
   private static String h(CosmeticType type) {
      throw new AssertionError();
   }

   @Inject(
      method = "a(Lnet/silentclient/common/api/services/cosmetics/Cosmetic;)Z",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$unlockAll(Cosmetic cosmetic, CallbackInfoReturnable<Boolean> cir) {
      if (cosmetic != null && FentUnlocks.type(cosmetic.getType())) {
         cir.setReturnValue(Boolean.TRUE);
      }
   }

   @Inject(
      method = "a(Lnet/silentclient/common/api/services/cosmetics/CosmeticType;Ljava/lang/String;)Z",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$equipLocally(CosmeticType type, String id, CallbackInfoReturnable<Boolean> cir) {
      String value = id == null ? "" : id;
      if (type == null || this.a(type) || !FentUnlocks.type(type) || fent$owns(type, value)) {
         return;
      }
      if (!value.isEmpty()) {
         try {
            if (this.c(type, value) == null) {
               return;
            }
         } catch (Throwable ignored) {
         }
      }
      try {
         this.b(type, value);
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not equip a cosmetic locally: " + throwable);
         return;
      }
      try {
         net.silentclient.fent.FentShare.publishLocal();
      } catch (Throwable ignored) {
      }
      cir.setReturnValue(Boolean.TRUE);
   }

   @Unique
   private boolean fent$owns(CosmeticType type, String id) {
      if (id.isEmpty()) {
         return false;
      }
      Map<String, Set<String>> owned = this.q;
      Set<String> ids = owned == null ? null : owned.get(h(type));
      return ids != null && ids.contains(id);
   }

   @Inject(method = "a(Ljava/util/Map;)V", at = @At("HEAD"), cancellable = true)
   private void fent$ignoreExpiries(Map<String, Map<String, Long>> expiries, CallbackInfo ci) {
      if (FentUnlocks.cosmetics()) {
         ci.cancel();
      }
   }

   @Inject(method = "a(Ljava/util/Map;J)V", at = @At("RETURN"))
   private void fent$reapplySaved(Map<CosmeticType, String> equipped, long generation, CallbackInfo ci) {
      try {
         fent$applySaved();
         fent$clearDisabledFakes();
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not reapply cosmetic picks: " + throwable);
      }
   }

   @Inject(
      method = "b(Lnet/silentclient/common/api/services/cosmetics/CosmeticType;Ljava/lang/String;)J",
      at = @At("RETURN")
   )
   private void fent$saveLocalSelection(CosmeticType type, String id, CallbackInfoReturnable<Long> cir) {
      if (type != null && FentUnlocks.type(type)) {
         fent$save(type, id == null ? "" : id);
      }
   }

   @Inject(method = "<init>", at = @At("RETURN"))
   private void fent$restoreSelections(CallbackInfo ci) {
      try {
         fent$applySaved();
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not restore cosmetic picks: " + throwable);
      }
   }

   @Unique
   private void fent$applySaved() {
      if (!FentUnlocks.cosmetics()) {
         return;
      }
      for (CosmeticType type : CosmeticType.values()) {
         if (type == null || this.a(type) || !FentUnlocks.type(type)) {
            continue;
         }
         String saved;
         try {
            saved = FentUnlocks.string(fent$key(type));
         } catch (Throwable ignored) {
            continue;
         }
         if (saved == null || saved.isEmpty()) {
            continue;
         }
         try {
            if (this.c(type, saved) == null) {
               continue;
            }
            String current = this.d(type);
            if (saved.equals(current)) {
               continue;
            }
            this.b(type, saved);
         } catch (Throwable throwable) {
            System.err.println("[Fent] could not reapply a cosmetic pick: " + throwable);
            continue;
         }
      }
   }

   @Unique
   private void fent$clearDisabledFakes() {
      boolean master;
      try {
         master = FentUnlocks.cosmetics();
      } catch (Throwable ignored) {
         return;
      }
      for (CosmeticType type : CosmeticType.values()) {
         if (type == null || this.a(type)) {
            continue;
         }
         boolean enabled;
         try {
            enabled = master && FentUnlocks.type(type);
         } catch (Throwable ignored) {
            continue;
         }
         if (enabled) {
            continue;
         }
         String saved;
         try {
            saved = FentUnlocks.string(fent$key(type));
         } catch (Throwable ignored) {
            continue;
         }
         if (saved == null || saved.isEmpty()) {
            continue;
         }
         try {
            String current = this.d(type);
            if (!saved.equals(current)) {
               continue;
            }
            this.b(type, "");
         } catch (Throwable ignored) {
         }
      }
   }

   @Unique
   private static void fent$save(CosmeticType type, String id) {
      FentUnlocks.setString(fent$key(type), id.isEmpty() ? "" : id);
   }

   @Unique
   private static String fent$key(CosmeticType type) {
      return FENT_PREFIX + type.name().toLowerCase(Locale.ROOT);
   }
}
