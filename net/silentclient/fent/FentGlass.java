package net.silentclient.fent;

import java.util.Map;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.internal.ac.d;

public final class FentGlass {
   public static final int NONE = 0;
   public static final int PANE = 1;
   public static final int SURFACE = 2;

   private static final float MIN_PANE_RADIUS = 3.8f;
   private static final float MIN_SURFACE = 10.0f;
   private static final int MIN_FILL_ALPHA = 0x05;

   private static final String[] PANE_KEYS = new String[] {
      "glass", "glass-tint", "hover-glass-tint", "glass-highlight", "border-width", "border",
      "glass-border-bottom"
   };
   private static final String[] SURFACE_KEYS = new String[] {"gradient-from", "gradient-to", "gradient-angle"};
   private static final String[] TEXT_KEYS = new String[] {"color"};

   private static final float WHITE_SATURATION = 0.25f;
   private static final String WHITE_TEXT = "#FFFFFFFF";

   private static final int RIM_WIDTH = 1;
   private static final String SURFACE_ANGLE_TEXT = Float.toString(FentMaterial.SURFACE_ANGLE);
   private static final String PANE_SHEEN_TEXT = Float.toString(FentMaterial.PANE_SHEEN);

   public static int kind(d node, boolean hover, float screenWidth, float screenHeight) {
      if (node == null || node.d == null) {
         return NONE;
      }
      Map<String, String> attributes = node.d;
      if (attributes.containsKey("glass")
         || attributes.containsKey("gradient-from")
         || attributes.containsKey("glow-color")
         || attributes.containsKey("skeleton")) {
         return NONE;
      }
      String classes = attributes.get("class");
      if (classes != null && classes.contains("scrim")) {
         return NONE;
      }
      if (screenWidth > 2.0f && screenHeight > 2.0f && node.h >= screenWidth - 4.0f && node.i >= screenHeight - 4.0f) {
         return NONE;
      }
      if ((fill(node, hover) >>> 24 & 0xFF) < MIN_FILL_ALPHA) {
         return NONE;
      }
      if (isPane(node, screenWidth, screenHeight)) {
         return PANE;
      }
      return node.h >= MIN_SURFACE && node.i >= MIN_SURFACE ? SURFACE : NONE;
   }

   private static boolean isPane(d node, float screenWidth, float screenHeight) {
      if (node == null || node.d == null) {
         return false;
      }
      String classes = node.d.get("class");
      if (classes != null && (classes.contains("window") || classes.contains("dialog"))) {
         return radius(node) >= MIN_PANE_RADIUS;
      }
      return false;
   }

   private static int fill(d node, boolean hover) {
      if (node == null || node.d == null) {
         return 0;
      }
      if (hover && node.d.containsKey("hover-bg")) {
         return node.b("hover-bg", 0);
      }
      return node.b("bg", 0);
   }

   public static String[] markPane(d node) {
      String[] saved = new String[PANE_KEYS.length];
      return markPane(node, saved) == null ? new String[0] : saved;
   }

   public static String[] markPane(d node, String[] saved) {
      if (node == null || node.d == null) {
         return null;
      }
      int accent = FentAero.accent();
      save(node, PANE_KEYS, saved);
      node.d.put("glass", "true");
      node.d.put("glass-tint", FentMaterial.hex(FentAero.base(), FentMaterial.PANE_FILL));
      node.d.put("hover-glass-tint", FentMaterial.hex(FentAero.base(), FentMaterial.PANE_FILL_HOVER));
      node.d.put("glass-highlight", PANE_SHEEN_TEXT);
      node.d.put("border-width", Integer.toString(RIM_WIDTH));
      node.d.put("border", FentMaterial.hex(FentMaterial.mix(0xFFFFFF, accent, 0.18f), FentMaterial.PANE_RIM));
      node.d.put("glass-border-bottom", FentMaterial.hex(0x000000, FentMaterial.PANE_RIM_BOTTOM));
      return saved;
   }

   public static void paneGradient(RenderBridge bridge, d node) {
      if (bridge == null || node == null || node.h < 1.0f || node.i < 1.0f) {
         return;
      }
      int base = FentAero.base();
      int accent = FentAero.accent();
      float all = node.c("rounded", 0.0f);
      gradient(
         bridge, node.f, node.g, node.h, node.i,
         node.c("rounded-tl", all), node.c("rounded-tr", all),
         node.c("rounded-br", all), node.c("rounded-bl", all),
         FentMaterial.argb(FentMaterial.PANE_GRADIENT_TOP, FentMaterial.lighten(base, 0.22f)),
         FentMaterial.argb(FentMaterial.PANE_GRADIENT_BOTTOM, accent)
      );
   }

   public static void paneShadow(RenderBridge bridge, d node) {
      if (bridge == null || node == null) {
         return;
      }
      bridge.drawShadow(
         node.f,
         node.g + FentMaterial.PANE_SHADOW_DROP,
         node.h,
         node.i,
         radius(node),
         FentMaterial.PANE_SHADOW_BLUR,
         FentMaterial.argb(FentAero.light() ? FentMaterial.PANE_SHADOW_LIGHT : FentMaterial.PANE_SHADOW_DARK, 0x000000)
      );
   }

   public static void restore(d node, String[] saved, String[] keys) {
      if (node == null || node.d == null || saved == null || keys == null) {
         return;
      }
      for (int index = 0; index < keys.length && index < saved.length; ++index) {
         if (saved[index] == null) {
            node.d.remove(keys[index]);
         } else {
            node.d.put(keys[index], saved[index]);
         }
      }
   }

   public static String[] surface(d node, boolean hover) {
      return surface(node, hover, new String[SURFACE_KEYS.length]);
   }

   public static String[] surface(d node, boolean hover, String[] saved) {
      if (node == null || node.d == null) {
         return null;
      }
      int accent = FentAero.accent();
      int fill = fill(node, hover);
      int alpha = FentMaterial.alphaOf(fill);
      if (alpha < MIN_FILL_ALPHA) {
         return null;
      }
      int rgb = FentMaterial.rgbOf(fill);
      int topRgb = FentMaterial.lighten(rgb, FentMaterial.SURFACE_LIFT);
      int bottomRgb = FentMaterial.mix(rgb, accent, FentMaterial.SURFACE_ACCENT);
      int topAlpha = Math.round(alpha * FentMaterial.SURFACE_TOP_ALPHA);
      int bottomAlpha = Math.round(alpha * FentMaterial.SURFACE_BOTTOM_ALPHA);
      if (topAlpha > 255) {
         topAlpha = 255;
      }
      if (bottomAlpha > 255) {
         bottomAlpha = 255;
      }
      save(node, SURFACE_KEYS, saved);
      node.d.put("gradient-from", FentMaterial.hex(topRgb, topAlpha));
      node.d.put("gradient-to", FentMaterial.hex(bottomRgb, bottomAlpha));
      node.d.put("gradient-angle", SURFACE_ANGLE_TEXT);
      return saved;
   }

   public static String[] surfaceKeys() {
      return SURFACE_KEYS;
   }

   public static String[] paneKeys() {
      return PANE_KEYS;
   }

   public static String[] textKeys() {
      return TEXT_KEYS;
   }

   public static int whiteForToken(String token, int colour) {
      if (token == null) {
         return 0;
      }
      String key = token.startsWith("@") ? token.substring(1) : token;
      if (!key.startsWith("text") || "textOnAccent".equals(key)) {
         return 0;
      }
      return whiteFor(colour);
   }

   public static int whiteFor(int colour) {
      int alpha = colour >>> 24 & 0xFF;
      if (alpha == 0) {
         return 0;
      }
      int rgb = FentMaterial.rgbOf(colour);
      if (rgb == 0xFFFFFF) {
         return alpha == 0xFF ? 0 : 0xFFFFFFFF;
      }
      if (FentMaterial.saturation(rgb) >= WHITE_SATURATION) {
         return 0;
      }
      return 0xFFFFFFFF;
   }

   public static String[] whiten(d node) {
      return null;
   }

   private static void save(d node, String[] keys, String[] saved) {
      if (node == null || node.d == null) {
         return;
      }
      for (int index = 0; index < keys.length; ++index) {
         saved[index] = node.d.get(keys[index]);
      }
   }

   public static void plate(RenderBridge bridge, float x, float y, float w, float h,
                            float r0, float r1, float r2, float r3, int plate) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      boolean material = FentAero.hudGlass() && !previewFlat();
      if (!material) {
         draw(bridge, x, y, w, h, r0, r1, r2, r3, plate);
         return;
      }
      int colour = plate;
      int alpha = FentMaterial.alphaOf(plate);
      if (alpha > 0) {
         boolean blurred = false;
         if (FentAero.refraction()) {
            blurred = nativeBlur(bridge, x, y, w, h, r0, r1, r2, r3);
            FentRefraction.Backdrop backdrop = null;
            if (!blurred) {
               backdrop = FentRefraction.capture(bridge, x, y, w, h, r0, r1, r2, r3);
            }
            if (blurred || backdrop != null) {
               if (backdrop != null) {
                  FentRefraction.draw(bridge, backdrop, x, y, w, h);
               }
               colour = FentMaterial.soften(plate, FentMaterial.BACKDROP_PLATE_ALPHA, FentMaterial.BACKDROP_PLATE_FLOOR);
            } else {
               colour = FentMaterial.soften(plate, FentMaterial.PLATE_ALPHA, FentMaterial.PLATE_FLOOR);
            }
         } else {
            colour = FentMaterial.soften(plate, FentMaterial.PLATE_ALPHA, FentMaterial.PLATE_FLOOR);
         }
      }
      draw(bridge, x, y, w, h, r0, r1, r2, r3, colour);
      hud(bridge, x, y, w, h, r0, r1, r2, r3, plate);
   }

   private static final float NATIVE_BLUR = 14.0f;

   private static boolean nativeBlur(RenderBridge bridge, float x, float y, float w, float h,
                                     float r0, float r1, float r2, float r3) {
      try {
         boolean available;
         try {
            available = bridge.backdropBlurAvailable();
         } catch (NoSuchMethodError missing) {
            return false;
         }
         if (!available) {
            return false;
         }
         try {
            bridge.drawBackdropBlur(x, y, w, h, NATIVE_BLUR, r0, r1, r2, r3);
            return true;
         } catch (NoSuchMethodError missing) {
            float radius = (r0 + r1 + r2 + r3) / 4.0f;
            try {
               bridge.drawBackdropBlur(x, y, w, h, radius > 0.0f ? radius : NATIVE_BLUR);
               return true;
            } catch (Throwable ignored) {
               return false;
            }
         }
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static void tile(RenderBridge bridge, float x, float y, float w, float h, float radius, int colour) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      if (!FentAero.hudGlass() || previewFlat()) {
         draw(bridge, x, y, w, h, radius, radius, radius, radius, colour);
         return;
      }
      int alpha = FentMaterial.alphaOf(colour);
      int plate = FentMaterial.soften(colour, FentMaterial.TILE_ALPHA, FentMaterial.TILE_FLOOR);
      draw(bridge, x, y, w, h, radius, radius, radius, radius, plate);
      if (alpha > 0 && h >= 3.0f) {
         float amount = Math.min(1.0f, alpha / 255.0f);
         sheen(
            bridge, x, y, w, h, radius, radius, radius, radius,
            FentMaterial.argb(Math.round(FentMaterial.TILE_SHEEN * amount), 0xFFFFFF)
         );
         int rim = FentMaterial.argb(Math.round(FentMaterial.TILE_RIM * amount),
            FentMaterial.luminance(FentMaterial.rgbOf(colour)) > 0.62f ? 0x000000 : 0xFFFFFF);
         if ((rim >>> 24 & 0xFF) > 0) {
            bridge.drawOutline(x, y, w, h, radius, RIM_WIDTH, rim);
         }
      }
   }

   public static void bar(RenderBridge bridge, float x, float y, float w, float h, float radius, int colour) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      if (!FentAero.hudGlass() || previewFlat()) {
         draw(bridge, x, y, w, h, radius, radius, radius, radius, colour);
         return;
      }
      draw(bridge, x, y, w, h, radius, radius, radius, radius,
         FentMaterial.soften(colour, FentMaterial.TILE_ALPHA, FentMaterial.TILE_FLOOR));
   }

   public static void bar(RenderBridge bridge, float x, float y, float w, float h,
                          float r0, float r1, float r2, float r3, int colour) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      if (!FentAero.hudGlass() || previewFlat()) {
         draw(bridge, x, y, w, h, r0, r1, r2, r3, colour);
         return;
      }
      draw(bridge, x, y, w, h, r0, r1, r2, r3,
         FentMaterial.soften(colour, FentMaterial.TILE_ALPHA, FentMaterial.TILE_FLOOR));
   }

   public static void image(RenderBridge bridge, String key, int[] pixels, int width, int height,
                            float x, float y, float iw, float ih, int tint) {
      if (bridge == null) {
         return;
      }
      if (!FentAero.hudGlass() || previewFlat()) {
         bridge.drawRgbaImage(key, pixels, width, height, x, y, iw, ih, tint);
         return;
      }
      bridge.drawRgbaImage(key, pixels, width, height, x, y, iw, ih,
         FentMaterial.soften(tint, FentMaterial.TILE_ALPHA, FentMaterial.TILE_FLOOR));
   }

   private static void hud(RenderBridge bridge, float x, float y, float w, float h,
                           float r0, float r1, float r2, float r3, int plate) {
      if (bridge == null || w < 4.0f || h < 4.0f) {
         return;
      }
      float amount = (plate >>> 24 & 0xFF) / 255.0f;
      if (amount <= 0.02f) {
         return;
      }
      boolean lightPlate = FentMaterial.luminance(FentMaterial.rgbOf(plate)) > 0.62f;
      int rim = lightPlate
         ? FentMaterial.argb(Math.round(0x3D * amount), 0x000000)
         : FentMaterial.argb(Math.round(0x4A * amount), 0xFFFFFF);
      if ((rim >>> 24 & 0xFF) == 0) {
         return;
      }
      float radius = (r0 + r1 + r2 + r3) / 4.0f;
      bridge.drawOutline(x, y, w, h, radius, RIM_WIDTH, rim);
   }

   private static void sheen(RenderBridge bridge, float x, float y, float w, float h,
                             float r0, float r1, float r2, float r3, int colour) {
      gradient(bridge, x, y, w, h, r0, r1, r2, r3, colour,
         FentMaterial.argb(0, FentMaterial.rgbOf(colour)));
   }

   private static boolean previewFlat() {
      return false;
   }

   private static void gradient(RenderBridge bridge, float x, float y, float w, float h,
                                float r0, float r1, float r2, float r3, int from, int to) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      try {
         net.silentclient.internal.x.a.a(bridge, bridge.deviceScale(), x, y, w, h, r0, r1, r2, r3, from, to, true);
      } catch (Throwable ignored) {
      }
   }

   private static void draw(RenderBridge bridge, float x, float y, float w, float h,
                            float r0, float r1, float r2, float r3, int colour) {
      if (r0 <= 0.01f && r1 <= 0.01f && r2 <= 0.01f && r3 <= 0.01f) {
         bridge.drawRect(x, y, w, h, colour);
      } else if (r0 == r1 && r1 == r2 && r2 == r3) {
         bridge.drawRoundedRect(x, y, w, h, r0, colour);
      } else {
         bridge.drawRoundedRectCorners(x, y, w, h, r0, r1, r2, r3, colour);
      }
   }

   private static float radius(d node) {
      return Math.max(
         node.c("rounded", 0.0f),
         Math.max(
            Math.max(node.c("rounded-tl", 0.0f), node.c("rounded-tr", 0.0f)),
            Math.max(node.c("rounded-br", 0.0f), node.c("rounded-bl", 0.0f))
         )
      );
   }

   private FentGlass() {
   }
}
