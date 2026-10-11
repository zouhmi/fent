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
   private static boolean refraction;
   private static int accentRgb = FALLBACK_ACCENT;
   private static int baseRgb = FALLBACK_BASE;
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
         boolean light = FentMaterial.luminance(base) >= 0.55f;
         // The material is drawn with the theme's own accent, so the glass in game and in the menus
         // carries the colour you picked on the Themes page, and with its base, which is what the
         // gradient on every surface is built from.
         accentRgb = accent;
         baseRgb = base;
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

   // The real refraction behind the Dynamic Widget island: it needs the HUD glass around it for the
   // edge, and its own switch because it pays for a framebuffer readback.
   public static boolean refraction() {
      refreshSwitches();
      return refraction;
   }

   // The theme's accent, for the rim light on glass surfaces.
   public static int accent() {
      return accentRgb;
   }

   // The theme's base colour: the gradient every surface is built from runs from this to the accent.
   public static int base() {
      return baseRgb;
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
         refraction = hudGlass && FentUnlocks.aeroRefraction();
      }
   }

   private static boolean resolve() throws Exception {
      themesClass = Class.forName(THEMES_CLASS);      themeClass = Class.forName(THEME_CLASS);
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
      // The two surfaces are what a window and a field are made of, and they are the ones that were
      // hiding the menu's blurred backdrop: nearly opaque before, well under half now. Everything a
      // card, a chip or a control is drawn with keeps its own alpha, because those are read at small
      // sizes and their body is what holds the text.
      put(palette, "surface", FentMaterial.argb(light ? 0xB4 : 0xA6, light ? base : FentMaterial.lighten(base, 0.06f)));
      put(palette, "surfaceRaised", FentMaterial.argb(light ? 0xA6 : 0x96, light ? FentMaterial.darken(base, 0.03f) : FentMaterial.lighten(base, 0.12f)));
      put(palette, "backdrop", FentMaterial.argb(light ? 0x4A : 0x66, 0x000000));
      put(palette, "inputBg", FentMaterial.argb(light ? 0x2E : 0x48, light ? 0xFFFFFF : 0x05060A));
      put(palette, "card", FentMaterial.argb(light ? 0x0E : 0x1A, wash));
      put(palette, "cardHover", FentMaterial.argb(light ? 0x17 : 0x26, wash));
      put(palette, "cardActive", FentMaterial.argb(light ? 0x20 : 0x30, wash));
      put(palette, "cardOutline", FentMaterial.argb(light ? 0x1A : 0x24, wash));
      put(palette, "cardOutlineStrong", FentMaterial.argb(light ? 0x33 : 0x42, wash));
      put(palette, "chip", FentMaterial.argb(light ? 0x0C : 0x14, wash));
      put(palette, "chipHover", FentMaterial.argb(light ? 0x18 : 0x20, wash));
      put(palette, "chipActive", FentMaterial.argb(light ? 0x22 : 0x2B, wash));
      put(palette, "control", FentMaterial.argb(light ? 0x12 : 0x1C, wash));
      put(palette, "controlHover", FentMaterial.argb(light ? 0x1F : 0x29, wash));
      put(palette, "controlLift", FentMaterial.argb(light ? 0x0C : 0x16, wash));
      put(palette, "controlActive", FentMaterial.argb(light ? 0x29 : 0x33, wash));
      put(palette, "controlOutline", FentMaterial.argb(light ? 0x24 : 0x2B, wash));
      put(palette, "controlOutlineHover", FentMaterial.argb(light ? 0x3D : 0x47, wash));
      put(palette, "outline", FentMaterial.argb(light ? 0x1F : 0x22, wash));
      put(palette, "scrollTrack", FentMaterial.argb(0x0F, wash));
      put(palette, "scrollThumb", FentMaterial.argb(light ? 0x29 : 0x33, wash));
      put(palette, "scrollThumbHover", FentMaterial.argb(light ? 0x4A : 0x59, wash));
      put(palette, "scrollThumbActive", FentMaterial.argb(light ? 0x66 : 0x73, wash));
      put(palette, "toggleOff", FentMaterial.argb(light ? 0x29 : 0x2E, wash));
      put(palette, "textPrimary", FentMaterial.argb(0xF2, light ? 0x0B0B12 : 0xFFFFFF));
      put(palette, "textOnBackdrop", FentMaterial.argb(0xFA, 0xFFFFFF));
      put(palette, "textBody", FentMaterial.argb(0xE2, light ? 0x14141C : 0xFFFFFF));
      put(palette, "textSecondary", FentMaterial.argb(light ? 0xB0 : 0xB8, light ? 0x1C1C26 : 0xFFFFFF));
      put(palette, "textMuted", FentMaterial.argb(light ? 0x8A : 0x8F, light ? 0x24242E : 0xFFFFFF));
      put(palette, "textFaint", FentMaterial.argb(light ? 0x66 : 0x5E, light ? 0x2C2C36 : 0xFFFFFF));
      put(palette, "accent", FentMaterial.argb(0xFF, accent));
      put(palette, "accentHover", FentMaterial.argb(0xFF, FentMaterial.lighten(accent, 0.14f)));
      put(palette, "textOnAccent", FentMaterial.argb(0xFF, FentMaterial.luminance(accent) > 0.62f ? 0x0B0B12 : 0xFFFFFF));
      put(palette, "accentOutline", FentMaterial.argb(0x66, accent));
      put(palette, "dangerSoft", FentMaterial.argb(light ? 0x24 : 0x2B, 0xFF453A));
      put(palette, "dangerHover", FentMaterial.argb(light ? 0x33 : 0x3D, 0xFF453A));
      put(palette, "dangerText", FentMaterial.argb(0xFF, light ? 0xC42A20 : 0xFF8080));
      put(palette, "controlDanger", FentMaterial.argb(light ? 0x2B : 0x33, 0xFF453A));
      put(palette, "controlDangerHover", FentMaterial.argb(light ? 0x3D : 0x4A, 0xFF453A));
      put(palette, "controlDangerText", FentMaterial.argb(0xFF, light ? 0xD63A2E : 0xFFA0A0));
   }

   // The theme card's preview is blended from four corners, so the glass reads as a frosted pane with
   // the accent glowing through the middle - the same base to accent gradient the real surfaces wear.
   private static int[] preview(int base, int accent) {
      return new int[] {
         FentMaterial.argb(0xFF, FentMaterial.lighten(base, 0.18f)),
         FentMaterial.argb(0xFF, base),
         FentMaterial.argb(0xFF, FentMaterial.mix(accent, 0xFFFFFF, 0.45f)),
         FentMaterial.argb(0xFF, accent)
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

   private FentAero() {
   }
}
