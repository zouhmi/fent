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

   // On means the active minute reports stay off.
   public static boolean coinsOff() {
      return on("coins.off");
   }

   // On means the Aero theme's Liquid Glass pass runs over the menu panels.
   public static boolean aeroGlass() {
      return on("aero.glass");
   }

   // On means a fetched track's lyrics are kept on disk and read back next time.
   public static boolean lyricsCache() {
      return on("lyrics.cache");
   }

   // One line for the startup log: the group switches plus any type that is off.
   public static String summary() {
      StringBuilder text = new StringBuilder("cosmetics=" + on("cosmetics"));
      for (CosmeticType type : CosmeticType.values()) {
         String name = type.name().toLowerCase(Locale.ROOT);
         if (!on("cosmetics." + name)) {
            text.append(' ').append(name).append("=off");
         }
      }
      text.append(" emotes=").append(on("emotes"));
      text.append(" themes=").append(on("themes"));
      text.append(" premium=").append(on("premium"));
      text.append(" coins.off=").append(on("coins.off"));
      text.append(" aero.glass=").append(on("aero.glass"));
      text.append(" lyrics.cache=").append(on("lyrics.cache"));
      return text.toString();
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
