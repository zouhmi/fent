package net.silentclient.fent.mixin;

import java.util.Locale;
import java.util.Map;
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

@Mixin(targets = "net.silentclient.internal.at.b")
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

   @Unique
   private static volatile java.lang.reflect.Method FENT_PUBLISH;

   @Unique
   private void fent$publish(CosmeticType type, String id) {
      try {
         java.lang.reflect.Method publish = FENT_PUBLISH;
         if (publish == null) {
            publish = findPublish(Class.forName("net.silentclient.internal.at.b"));
            if (publish != null) {
               FENT_PUBLISH = publish;
            } else {
               throw new NoSuchMethodException("f(CosmeticType,String)");
            }
         }
         publish.invoke(this, type, id);
      } catch (Throwable throwable) {
         throw new RuntimeException(throwable);
      }
   }

   @Unique
   private static java.lang.reflect.Method findPublish(Class<?> type) {
      try {
         java.lang.reflect.Method publish = type.getDeclaredMethod("f",
            CosmeticType.class, String.class);
         publish.setAccessible(true);
         return publish;
      } catch (Throwable ignored) {
         return null;
      }
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
      if (type == null || this.a(type) || !FentUnlocks.type(type)) {
         return;
      }
      String value = id == null ? "" : id;
      if (!value.isEmpty()) {
         try {
            if (this.c(type, value) == null) {
               return;
            }
         } catch (Throwable ignored) {
         }
      }
      try {
         try {
            if (this.b(type) != null) {
               net.silentclient.internal.an.o.a(type, "");
            }
         } catch (Throwable ignored) {
         }
         this.b(type, value);
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not equip a cosmetic locally: " + throwable);
         return;
      }
      try {
         fent$publish(type, value);
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not publish a local cosmetic pick: " + throwable);
      }
      fent$save(type, value);
      try {
         net.silentclient.fent.FentShare.publishLocal();
      } catch (Throwable ignored) {
      }
      cir.setReturnValue(Boolean.TRUE);
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
            if (this.b(type) != null) {
               try {
                  net.silentclient.internal.an.o.a(type, "");
               } catch (Throwable ignored) {
               }
            }
            this.b(type, saved);
         } catch (Throwable throwable) {
            System.err.println("[Fent] could not reapply a cosmetic pick: " + throwable);
            continue;
         }
         try {
            fent$publish(type, saved);
         } catch (Throwable throwable) {
            System.err.println("[Fent] could not publish a cosmetic pick: " + throwable);
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
            try {
               fent$publish(type, "");
            } catch (Throwable ignored) {
            }
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
