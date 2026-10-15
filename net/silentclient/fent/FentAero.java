package net.silentclient.fent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FentAero {
   public static final String ID = "liquidglass";
   private static final String NAME = "Liquid Glass";
private static final String THEMES_CLASS = "net.silentclient.internal.ax.a";
private static final String THEME_CLASS = "net.silentclient.internal.ax.a$a";
   private static final String DARK_TEMPLATE = "midnight";
   private static final String LIGHT_TEMPLATE = "porcelain";
   private static final int FALLBACK_BASE = 0x14161B;
   private static final int FALLBACK_ACCENT = 0x4C9FFF;
   private static final long ACTIVE_TTL_NANOS = 200000000L;

   private static volatile boolean broken;
   private static volatile Class<?> themesClass;
   private static volatile Constructor<?> themeConstructor;
   private static volatile Field themeList;
   private static volatile Field themeMap;
   private static volatile Field themeTokens;
   private static volatile Field previewCache;
   private static volatile long glassCheckedAt;
   private static volatile boolean glass;
   private static volatile boolean hudGlass;
   private static volatile boolean refraction;
   private static volatile int accentRgb = FALLBACK_ACCENT;
   private static volatile int baseRgb = FALLBACK_BASE;
   private static volatile boolean lightBase;

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
         accentRgb = accent;
         baseRgb = base;
         lightBase = light;
         Object template = themesClass.getMethod("a", String.class).invoke(null, light ? LIGHT_TEMPLATE : DARK_TEMPLATE);
         if (template == null) {
            template = themesClass.getMethod("a", String.class).invoke(null, DARK_TEMPLATE);
         }
         if (template == null) {
            return;
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
         Object cache = previewCache.get(null);
          if (cache instanceof Map) {
             try {
                ((Map<?, ?>)cache).remove(ID);
             } catch (Throwable ignored) {
             }
          }
      } catch (Throwable throwable) {
         broken = true;
         System.err.println("[Fent] could not register the Liquid Glass theme: " + throwable);
      }
   }

   public static boolean active() {
      try {
         return ID.equals(net.silentclient.internal.ax.a.i());
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean glass() {
      refreshSwitches();
      return glass;
   }

   public static boolean hudGlass() {
      refreshSwitches();
      return hudGlass;
   }

   public static boolean refraction() {
      refreshSwitches();
      return refraction;
   }

   public static int accent() {
      return accentRgb;
   }

   public static int base() {
      return baseRgb;
   }

   public static boolean light() {
      return lightBase;
   }

   private static void refreshSwitches() {
      long now = System.nanoTime();
      if (now - glassCheckedAt >= ACTIVE_TTL_NANOS || now < glassCheckedAt) {
         glassCheckedAt = now;
         boolean on = active();
         glass = on && FentUnlocks.liquidGlassUi();
         hudGlass = on && FentUnlocks.liquidGlassHud();
         refraction = hudGlass && FentUnlocks.liquidGlassRefraction();
      }
   }

   private static boolean resolve() throws Exception {
      themesClass = Class.forName(THEMES_CLASS);
      Class<?> themeClass = Class.forName(THEME_CLASS);
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

   private static void material(Map<String, Integer> palette, int base, int accent, boolean light) {
      int wash = light ? 0x000000 : 0xFFFFFF;
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
      put(palette, "textPrimary", FentMaterial.argb(0xFF, light ? 0x0B0B12 : 0xFFFFFF));
      put(palette, "textOnBackdrop", FentMaterial.argb(0xFF, light ? 0x0B0B12 : 0xFFFFFF));
      put(palette, "textBody", FentMaterial.argb(0xFF, light ? 0x14141C : 0xFFFFFF));
      put(palette, "textSecondary", FentMaterial.argb(0xFF, light ? 0x14141C : 0xFFFFFF));
      put(palette, "textMuted", FentMaterial.argb(0xFF, light ? 0x1C1C26 : 0xFFFFFF));
      put(palette, "textFaint", FentMaterial.argb(0xFF, light ? 0x24242E : 0xFFFFFF));
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
