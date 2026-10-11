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

   // On means the same material reaches the in-game HUD widgets.
   public static boolean aeroHud() {
      return on("aero.hud");
   }

   // On means the Dynamic Widget island samples the world behind it and bends it, so the glass shows
   // what is under the widget instead of only lighting its edge.
   public static boolean aeroRefraction() {
      return on("aero.refraction");
   }

   // On means a fetched track's lyrics are kept on disk and read back next time.
   public static boolean lyricsCache() {
      return on("lyrics.cache");
   }

   // On means a settings row keeps no plate, so its contents sit straight on the menu backdrop.
   public static boolean transparentRows() {
      return on("rows.transparent");
   }

   // On means a colour setting's swatch shows the colour but no longer opens the colour editor.
   public static boolean hideColorPicker() {
      return on("colorpicker.off");
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
