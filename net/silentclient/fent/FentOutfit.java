package net.silentclient.fent;

import java.util.Locale;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.internal.aw.d;

/**
 * Outfit memory: the equip-packet hooks re-apply saved picks when packets
 * arrive, but packets do not cover every reset (world/server switches without
 * a fresh packet, singleplayer, catalog still loading after a relaunch, or a
 * refresh path with no hook). This slow tick re-asserts the saved outfit so
 * it survives all of those, a few seconds at most after the catalog is ready.
 */
public final class FentOutfit {
   private static final String PREFIX = "fent.selected.";
   private static final long REAPPLY_EVERY_NANOS = 5000000000L;
   private static volatile long lastNanos;

   /**
    * Called from the render paths (menu nodes + HUD plates/tiles/bars), so no
    * extra Fabric lifecycle dependency is needed. Time-throttled to ~5s.
    */
   public static void tick() {
      long now = System.nanoTime();
      if (now - lastNanos < REAPPLY_EVERY_NANOS && now >= lastNanos) {
         return;
      }
      lastNanos = now;
      try {
         reapply();
      } catch (Throwable ignored) {
      }
   }

   static void reapply() {
      d store = store();
      if (store == null) {
         return;
      }
      for (CosmeticType type : CosmeticType.values()) {
         if (type == null) {
            continue;
         }
         boolean enabled;
         try {
            enabled = FentUnlocks.type(type);
         } catch (Throwable ignored) {
            continue;
         }
         String saved;
         try {
            saved = FentUnlocks.string(key(type));
         } catch (Throwable ignored) {
            continue;
         }
         if (saved == null) {
            saved = "";
         }
         try {
            if (enabled) {
               if (saved.isEmpty() || !idOk(saved)) {
                  continue;
               }
               if (store.c(type, saved) == null) {
                  continue;
               }
               if (saved.equals(store.d(type))) {
                  continue;
               }
               store.b(type, saved);
            } else {
               if (store.a(type)) {
                  continue;
               }
               if (saved.isEmpty() || !saved.equals(store.d(type))) {
                  continue;
               }
               store.b(type, "");
            }
         } catch (Throwable ignored) {
         }
      }
   }

   private static d store() {
      try {
         return d.e();
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static String key(CosmeticType type) {
      return PREFIX + type.name().toLowerCase(Locale.ROOT);
   }

   private static boolean idOk(String id) {
      if (id.length() > 64) {
         return false;
      }
      for (int index = 0; index < id.length(); ++index) {
         char c = id.charAt(index);
         boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
         if (!ok) {
            return false;
         }
      }
      return true;
   }

   private FentOutfit() {
   }
}