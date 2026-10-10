package net.silentclient.fent;

import net.silentclient.internal.ai.r;

// Reads the Unlocker toggles from SilentClient's settings store.
// Keys look like setting.unlocker.cosmetics, written by the settings screen.
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

   private FentUnlocks() {
   }
}
