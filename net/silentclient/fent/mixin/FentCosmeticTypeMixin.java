package net.silentclient.fent.mixin;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.silentclient.common.api.services.cosmetics.Cosmetic;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.common.api.services.cosmetics.EquippedCosmetics;
import net.silentclient.fent.FentShare;
import net.silentclient.fent.FentUnlocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
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
   @Shadow
   @Final
   private volatile java.util.concurrent.atomic.AtomicLong H;

   @Shadow
   private synchronized void a(Map<CosmeticType, String> items, long generation) {
      throw new AssertionError();
   }

   @Shadow
   private void f(CosmeticType type, String id) {
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

   /**
    * The equip button icon/picker rows route through c(Cosmetic), not through
    * a(Cosmetic). Without this hook icons demand real ownership even when the
    * Fent unlocker is on.
    */
   @Inject(
      method = "c(Lnet/silentclient/common/api/services/cosmetics/Cosmetic;)Z",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$unlockEquipGate(Cosmetic cosmetic, CallbackInfoReturnable<Boolean> cir) {
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
         this.f(type, value);
      } catch (Throwable ignored) {
      }
      try {
         net.silentclient.fent.FentShare.publishLocal();
      } catch (Throwable ignored) {
      }
      cir.setReturnValue(Boolean.TRUE);
   }

   /**
    * a(CosmeticType,String) is the one entry point every equip path uses.
    * Already-owned equips short-circuit to p.a(type,id) without the local
    * mirror ever being touched, so saving at its tail covers both the owned
    * and the Fent-unlocked paths, and lets the sync API publish every change.
    */
   @Inject(
      method = "a(Lnet/silentclient/common/api/services/cosmetics/CosmeticType;Ljava/lang/String;)Z",
      at = @At("TAIL")
   )
   private void fent$saveSelectionAfterEquip(CosmeticType type, String id, CallbackInfoReturnable<Boolean> cir) {
      if (type == null || !cir.getReturnValueZ() || !FentUnlocks.type(type)) {
         return;
      }
      String value;
      if (id != null && !id.isEmpty()) {
         // The id being equipped is authoritative; prefer it over any stale
         // read-back mirror value so a fresh locker pick is never clobbered
         // before it gets published to peers.
         value = id;
      } else {
         String saved = null;
         try {
            saved = net.silentclient.internal.aq.p.b(type);
         } catch (Throwable ignored) {
         }
         value = saved == null ? "" : saved;
      }
      if (!value.isEmpty() && !idOk(value)) {
         return;
      }
      fent$save(type, value);
      try {
         net.silentclient.fent.FentShare.publishLocal();
      } catch (Throwable ignored) {
      }
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

   @Unique
   private boolean idOk(String id) {
      if (id.length() > 64) {
         return false;
      }
      for (int i = 0; i < id.length(); ++i) {
         char c = id.charAt(i);
         boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
         if (!ok) {
            return false;
         }
      }
      return true;
   }

   @Inject(method = "a(Ljava/util/Map;)V", at = @At("HEAD"), cancellable = true)
   private void fent$ignoreExpiries(Map<String, Map<String, Long>> expiries, CallbackInfo ci) {
      if (FentUnlocks.cosmetics()) {
         ci.cancel();
      }
   }

   /**
    * Joining a new server or world pushes fresh equipped-cosmetics packets whose
    * generation is older than the local selection but newer than the last known
    * local generation, wiping locally unlocked cosmetics. Merge our saved
    * selections back in and hand SilentClient the merged map, so nothing
    * disappears in the first place.
    */
   @Inject(
      method = "onEquippedChanged(Lnet/silentclient/common/api/services/cosmetics/EquippedCosmetics;)V",
      at = @At("HEAD"),
      cancellable = true
   )
   private void fent$keepLocalSelections(EquippedCosmetics equipped, CallbackInfo ci) {
      if (!FentUnlocks.cosmetics() || equipped == null) {
         return;
      }
      Map<CosmeticType, String> incoming = equipped.getItems();
      if (incoming == null || incoming.isEmpty()) {
         return;
      }
      boolean touched = false;
      Map<CosmeticType, String> merged = new EnumMap<CosmeticType, String>(CosmeticType.class);
      merged.putAll(incoming);
      for (CosmeticType type : CosmeticType.values()) {
         if (type == null || this.a(type) || !FentUnlocks.type(type)) {
            continue;
         }
         String saved = FentUnlocks.string(fent$key(type));
         if (saved == null || saved.isEmpty() || !idOk(saved)) {
            continue;
         }
         try {
            if (this.c(type, saved) == null) {
               continue;
            }
         } catch (Throwable ignored) {
            continue;
         }
         merged.put(type, saved);
         touched = true;
      }
      if (!touched) {
         return;
      }
      try {
         this.a(merged, H.get());
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not merge mirrored cosmetic packets: " + throwable);
         return;
      }
      ci.cancel();
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
      // Any cosmetic selection change - silent client equip, fent override, restore,
      // disable-clear - now funnels through here, so trigger the fent-to-fent publish
      // from here to guarantee changes made while inside a server are always broadcast.
      try {
         net.silentclient.fent.FentShare.publishLocal();
      } catch (Throwable ignored) {
      }
   }

   @Unique
   private static String fent$key(CosmeticType type) {
      return FENT_PREFIX + type.name().toLowerCase(Locale.ROOT);
   }
}
