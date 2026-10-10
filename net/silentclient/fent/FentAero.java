package net.silentclient.fent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The Aero theme: Apple's Liquid Glass material, built from the Base and Accent colours that the
// Themes page already edits, so changing those changes Aero too.
//
// SilentClient holds its themes in two private static collections inside
// net.silentclient.internal.av.a (a list for the picker, a map by id), filled by a static block.
// A mixin cannot call the private builder that fills them, so this class takes an existing theme as
// the token template, replaces the tokens that carry the material, and registers the result under
// the id "aero". Every other token, and the fallbacks for tokens a later client may add, come from
// that template, which is why nothing here has to know the full token list.
public final class FentAero {
   public static final String ID = "aero";
   private static final String NAME = "Aero";
   private static final String THEMES_CLASS = "net.silentclient.internal.av.a";
   private static final String THEME_CLASS = "net.silentclient.internal.av.a$a";
   private static final String DARK_TEMPLATE = "midnight";
   private static final String LIGHT_TEMPLATE = "porcelain";
   private static final int FALLBACK_BASE = 0x14161B;
   private static final int FALLBACK_ACCENT = 0x4C9FFF;
   // The active theme is read through the settings store, and the glass pass asks for it once per
   // panel, so the answer is held for a moment. A theme switch still lands on the next frame.
   private static final long ACTIVE_TTL_MS = 200L;

   private static boolean broken;
   private static Class<?> themesClass;
   private static Class<?> themeClass;
   private static Constructor<?> themeConstructor;
   private static Field themeList;
   private static Field themeMap;
   private static Field themeTokens;
   private static Field previewCache;
   private static long glassCheckedAt;
   private static boolean glass;
   private static boolean hudGlass;
   private static int accentRgb = FALLBACK_ACCENT;
   private static boolean lightBase;

   // Called from the theme system after its own themes are built and whenever it rebuilds the
   // custom theme, which is the point where the Base and Accent colours are applied.
   public static synchronized void refresh(String baseHex, String accentHex) {
      if (broken) {
         return;
      }
      try {
         if (themesClass == null && !resolve()) {
            return;
         }
         int base = rgb(baseHex, FALLBACK_BASE);
         int accent = rgb(accentHex, FALLBACK_ACCENT);
         boolean light = luminance(base) >= 0.55f;
         // The material is drawn with the theme's own accent, so the glass in game and in the menus
         // carries the colour you picked on the Themes page.
         accentRgb = accent;
         lightBase = light;
         Object template = themesClass.getMethod("a", String.class).invoke(null, light ? LIGHT_TEMPLATE : DARK_TEMPLATE);
         if (template == null) {
            template = themesClass.getMethod("a", String.class).invoke(null, DARK_TEMPLATE);
         }
         Map<String, Integer> palette = new LinkedHashMap<String, Integer>(tokensOf(template));
         material(palette, base, accent, light);
         Object theme = themeConstructor.newInstance(ID, NAME, palette, preview(base, accent));
         List<Object> list = asList(themeList.get(null));
         Map<Object, Object> map = asMap(themeMap.get(null));
         Object previous = map.get(ID);
         int index = previous == null ? -1 : list.indexOf(previous);
         if (index < 0) {
            list.add(theme);
         } else {
            list.set(index, theme);
         }
         map.put(ID, theme);
         // The 64x64 theme preview is cached by theme id, so the cache has to go or a colour change
         // would keep showing the old card.
         Object cache = previewCache.get(null);
         if (cache instanceof Map) {
            ((Map<?, ?>)cache).clear();
         }
      } catch (Throwable throwable) {
         broken = true;
         System.err.println("[Fent] could not register the Aero theme: " + throwable);
      }
   }

   // The theme in use.
   public static boolean active() {
      try {
         return ID.equals(net.silentclient.internal.av.a.i());
      } catch (Throwable throwable) {
         return false;
      }
   }

   // Aero's Liquid Glass pass over the menu panels, with its own switch. Asked once per panel, so
   // both the theme and the switch are held for a moment; a change still lands on the next frame.
   public static boolean glass() {
      refreshSwitches();
      return glass;
   }

   // The same pass over the in-game HUD widgets. Its own switch, so a machine that minds the extra
   // draws can keep the menus glassed and the HUD flat.
   public static boolean hudGlass() {
      refreshSwitches();
      return hudGlass;
   }

   // The theme's accent, for the rim light on glass surfaces.
   public static int accent() {
      return accentRgb;
   }

   // A light base gets the light material: a softer shadow and a darker edge.
   public static boolean light() {
      return lightBase;
   }

   private static void refreshSwitches() {
      long now = System.currentTimeMillis();
      if (now - glassCheckedAt >= ACTIVE_TTL_MS) {
         glassCheckedAt = now;
         boolean on = active();
         glass = on && FentUnlocks.aeroGlass();
         hudGlass = on && FentUnlocks.aeroHud();
      }
   }

   private static boolean resolve() throws Exception {
      themesClass = Class.forName(THEMES_CLASS);
      themeClass = Class.forName(THEME_CLASS);
      themeConstructor = themeClass.getDeclaredConstructor(String.class, String.class, Map.class, int[].class);
      themeConstructor.setAccessible(true);
      themeList = themesClass.getDeclaredField("j");
      themeList.setAccessible(true);
      themeMap = themesClass.getDeclaredField("k");
      themeMap.setAccessible(true);
      themeTokens = themeClass.getDeclaredField("d");
      themeTokens.setAccessible(true);
      previewCache = themesClass.getDeclaredField("q");
      previewCache.setAccessible(true);
      return true;
   }

   @SuppressWarnings("unchecked")
   private static List<Object> asList(Object value) {
      return (List<Object>)value;
   }

   @SuppressWarnings("unchecked")
   private static Map<Object, Object> asMap(Object value) {
      return (Map<Object, Object>)value;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Integer> tokensOf(Object theme) throws Exception {
      return (Map<String, Integer>)themeTokens.get(theme);
   }

   // The material. Surfaces are translucent over the blurred menu backdrop, controls and cards are
   // faint washes instead of plates, borders are hairlines, and text keeps its contrast so the
   // translucency never costs legibility. Dark bases get white washes, light bases black ones.
   private static void material(Map<String, Integer> palette, int base, int accent, boolean light) {
      int wash = light ? 0x000000 : 0xFFFFFF;
      put(palette, "surface", argb(light ? 0xE0 : 0xD2, light ? base : lighten(base, 0.04f)));
      put(palette, "surfaceRaised", argb(light ? 0xCC : 0xBE, light ? darken(base, 0.03f) : lighten(base, 0.10f)));
      put(palette, "backdrop", argb(light ? 0x59 : 0x73, 0x000000));
      put(palette, "inputBg", argb(light ? 0x38 : 0x59, light ? 0xFFFFFF : 0x05060A));
      put(palette, "card", argb(light ? 0x0E : 0x1A, wash));
      put(palette, "cardHover", argb(light ? 0x17 : 0x26, wash));
      put(palette, "cardActive", argb(light ? 0x20 : 0x30, wash));
      put(palette, "cardOutline", argb(light ? 0x1A : 0x24, wash));
      put(palette, "cardOutlineStrong", argb(light ? 0x33 : 0x42, wash));
      put(palette, "chip", argb(light ? 0x0C : 0x14, wash));
      put(palette, "chipHover", argb(light ? 0x18 : 0x20, wash));
      put(palette, "chipActive", argb(light ? 0x22 : 0x2B, wash));
      put(palette, "control", argb(light ? 0x12 : 0x1C, wash));
      put(palette, "controlHover", argb(light ? 0x1F : 0x29, wash));
      put(palette, "controlLift", argb(light ? 0x0C : 0x16, wash));
      put(palette, "controlActive", argb(light ? 0x29 : 0x33, wash));
      put(palette, "controlOutline", argb(light ? 0x24 : 0x2B, wash));
      put(palette, "controlOutlineHover", argb(light ? 0x3D : 0x47, wash));
      put(palette, "outline", argb(light ? 0x1F : 0x22, wash));
      put(palette, "scrollTrack", argb(0x0F, wash));
      put(palette, "scrollThumb", argb(light ? 0x29 : 0x33, wash));
      put(palette, "scrollThumbHover", argb(light ? 0x4A : 0x59, wash));
      put(palette, "scrollThumbActive", argb(light ? 0x66 : 0x73, wash));
      put(palette, "toggleOff", argb(light ? 0x29 : 0x2E, wash));
      put(palette, "textPrimary", argb(0xF2, light ? 0x0B0B12 : 0xFFFFFF));
      put(palette, "textOnBackdrop", argb(0xFA, 0xFFFFFF));
      put(palette, "textBody", argb(0xE2, light ? 0x14141C : 0xFFFFFF));
      put(palette, "textSecondary", argb(light ? 0xB0 : 0xB8, light ? 0x1C1C26 : 0xFFFFFF));
      put(palette, "textMuted", argb(light ? 0x8A : 0x8F, light ? 0x24242E : 0xFFFFFF));
      put(palette, "textFaint", argb(light ? 0x66 : 0x5E, light ? 0x2C2C36 : 0xFFFFFF));
      put(palette, "accent", argb(0xFF, accent));
      put(palette, "accentHover", argb(0xFF, lighten(accent, 0.14f)));
      put(palette, "textOnAccent", argb(0xFF, luminance(accent) > 0.62f ? 0x0B0B12 : 0xFFFFFF));
      put(palette, "accentOutline", argb(0x66, accent));
      put(palette, "dangerSoft", argb(light ? 0x24 : 0x2B, 0xFF453A));
      put(palette, "dangerHover", argb(light ? 0x33 : 0x3D, 0xFF453A));
      put(palette, "dangerText", argb(0xFF, light ? 0xC42A20 : 0xFF8080));
      put(palette, "controlDanger", argb(light ? 0x2B : 0x33, 0xFF453A));
      put(palette, "controlDangerHover", argb(light ? 0x3D : 0x4A, 0xFF453A));
      put(palette, "controlDangerText", argb(0xFF, light ? 0xD63A2E : 0xFFA0A0));
   }

   // The theme card's preview is blended from four corners, so the glass reads as a frosted pane
   // with the accent glowing through the middle.
   private static int[] preview(int base, int accent) {
      return new int[] {
         argb(0xFF, lighten(base, 0.18f)),
         argb(0xFF, base),
         argb(0xFF, mix(accent, 0xFFFFFF, 0.45f)),
         argb(0xFF, accent)
      };
   }

   private static void put(Map<String, Integer> palette, String token, int color) {
      palette.put(token, Integer.valueOf(color));
   }

   private static int rgb(String text, int fallback) {
      if (text == null) {
         return fallback;
      }
      String value = text.trim();
      if (value.startsWith("#")) {
         value = value.substring(1);
      }
      // The theme colours are written #RRGGBBAA.
      if (value.length() == 8) {
         value = value.substring(0, 6);
      }
      if (value.length() != 6) {
         return fallback;
      }
      try {
         return (int)(Long.parseLong(value, 16) & 0xFFFFFFL);
      } catch (NumberFormatException exception) {
         return fallback;
      }
   }

   private static int argb(int alpha, int rgb) {
      return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
   }

   private static int lighten(int rgb, float amount) {
      return mix(rgb, 0xFFFFFF, amount);
   }

   private static int darken(int rgb, float amount) {
      return mix(rgb, 0x000000, amount);
   }

   private static int mix(int from, int to, float amount) {
      float t = Math.max(0.0f, Math.min(1.0f, amount));
      int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
      return red << 16 | green << 8 | blue;
   }

   private static float luminance(int rgb) {
      return (0.299f * (rgb >> 16 & 0xFF) + 0.587f * (rgb >> 8 & 0xFF) + 0.114f * (rgb & 0xFF)) / 255.0f;
   }

   private FentAero() {
   }
}
