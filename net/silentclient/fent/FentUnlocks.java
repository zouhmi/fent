package net.silentclient.fent;

import java.util.Locale;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.internal.ai.r;

// Reads the Unlocker toggles from SilentClient's settings store.
// Keys look like setting.unlocker.cosmetics and setting.unlocker.cosmetics.cape.
public final class FentUnlocks {
   // Unset means on, so a fresh install unlocks without touching the screen.
   private static boolean on(String key) {
      String value = r.d("setting.unlocker." + key);
      return value.isEmpty() || Boolean.parseBoolean(value);
   }

   public static boolean cosmetics() {
      return on("cosmetics");
   }

   public static boolean emotes() {
      return on("emotes");
   }

   public static boolean themes() {
      return on("themes");
   }

   public static boolean premium() {
      return on("premium");
   }

   // Master switch plus the per type switch, so capes, wings, pets and auras can differ.
   public static boolean type(CosmeticType type) {
      if (type == null) {
         return cosmetics();
      }
      return cosmetics() && on("cosmetics." + type.name().toLowerCase(Locale.ROOT));
   }

   private FentUnlocks() {
   }
}
