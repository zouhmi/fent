package net.silentclient.fent;

import java.util.Map;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.internal.K.f;

// The Aero material, and the rule for what gets it.
//
// Every menu node is painted by net.silentclient.internal.K.g, and the order there decides how a
// surface can be reached. A node carrying the "glass" attribute takes the engine's Liquid Glass path
// - translucent fill, a white sheen decaying from the top edge, a hairline rim - and the painter
// returns, so a glass node never reaches the gradient steps. That is why a pane is a pane:
//
//   * a window, a dialog or a plate large enough to read as one gets the full material. Its fill is
//     an explicit low alpha rather than the theme's opaque surface token, its gradient is drawn under
//     that fill, and it keeps the engine's sheen and rim. Over the client's own blurred menu backdrop
//     the result is a sheet you can see through.
//
//   * every other plate - a card, a row, a well, a field, a pill - keeps its own fill and its own
//     hover colour, and gains the gradient: "gradient-from" and "gradient-to" are written just before
//     the node is drawn and taken back off just after, so the engine's own gradient step replaces the
//     flat fill with a lit one. Nothing else about the node changes, which is what keeps hover, the
//     template's border and the layout untouched.
//
// The gradient's first stop is derived from the node's own colour - lifted, and taken from the hover
// colour while the pointer is on it - and the second pulls that colour toward the theme's accent. The
// surface therefore stays the colour the theme gave it, and only the light across it changes.
//
// Cost is why the material stays on surfaces and never grows a sheen: the painter spends eight
// clipped passes on the highlight and up to eight more on a two colour rim, and doing that to fifty
// cards is what froze the cosmetics menu once. A gradient is one draw.
public final class FentGlass {
   // What the material applies to.
   public static final int NONE = 0;
   public static final int PANE = 1;
   public static final int SURFACE = 2;

   // Menu panes: the windows and the dialogs, marked by class. Everything else in the menu is a
   // surface, and takes only the gradient - the size heuristics that used to promote big plates to
   // panes spent an expensive material on whatever happened to be large, including the very section
   // plates that hold a screen's content, and a pane with no backdrop visible behind it reads as an
   // invisible sheet: the transparency the material is built from, with nothing to show through.
   private static final float MIN_PANE_RADIUS = 3.8f;
   // Below this a plate is an icon hit box, not a surface: a 9 unit chip gains nothing from a
   // gradient, so the material leaves it alone and keeps its hover wash.
   private static final float MIN_SURFACE = 10.0f;
   // A plate this faint is still a plate: the built in themes draw their cards around alpha 14, and a
   // card that is skipped here keeps the flat fill the material exists to replace.
   private static final int MIN_FILL_ALPHA = 0x05;

   private static final String[] PANE_KEYS = new String[] {
      "glass", "glass-tint", "hover-glass-tint", "glass-highlight", "border-width", "border",
      "glass-border-bottom"
   };
   private static final String[] SURFACE_KEYS = new String[] {"gradient-from", "gradient-to", "gradient-angle"};

   private static final int RIM_WIDTH = 1;

   // Classifies a menu node. hover is the painter's own hover state, because a surface's gradient is
   // built from the colour it is about to be filled with, which changes under the pointer.
   public static int kind(f node, boolean hover, float screenWidth, float screenHeight) {
      if (node == null) {
         return NONE;
      }
      Map<String, String> attributes = node.b;
      // Already material: glass, a gradient of its own, a glow or a skeleton the client animates.
      if (attributes.containsKey("glass")
         || attributes.containsKey("gradient-from")
         || attributes.containsKey("glow-color")
         || attributes.containsKey("skeleton")) {
         return NONE;
      }
      // A scrim is the dimming behind a dialog and the full screen node is the menu itself; neither
      // is a surface, and frosting them would tint everything.
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

   // A pane is a window or a dialog by class, or a plate whose own size and radius say it is one.
   // Everything else is a surface, and gets only the gradient.
   private static boolean isPane(f node, float screenWidth, float screenHeight) {
      String classes = node.b.get("class");
      if (classes != null && (classes.contains("window") || classes.contains("dialog"))) {
         return radius(node) >= MIN_PANE_RADIUS;
      }
      return false;
   }

   // The colour a node is about to be filled with, hover colour first when the pointer is on it.
   private static int fill(f node, boolean hover) {
      if (hover && node.b.containsKey("hover-bg")) {
         return node.b("hover-bg", 0);
      }
      return node.b("bg", 0);
   }

   // ----------------------------------------------------------------------------------------------
   // Panes
   // ----------------------------------------------------------------------------------------------

   // Marks a node as a pane. The fill is written out in full rather than left to the theme's surface
   // token, which is what used to hide the backdrop, and the rim replaces whatever border the theme
   // gave the node: the bottom of it is a shade darker than the top, which is what the painter turns
   // into a lit edge.
   public static String[] markPane(f node) {
      int accent = FentAero.accent();
      String[] saved = save(node, PANE_KEYS);
      node.b.put("glass", "true");
      node.b.put("glass-tint", FentMaterial.hex(FentAero.base(), FentMaterial.PANE_FILL));
      node.b.put("hover-glass-tint", FentMaterial.hex(FentAero.base(), FentMaterial.PANE_FILL_HOVER));
      node.b.put("glass-highlight", Float.toString(FentMaterial.PANE_SHEEN));
      node.b.put("border-width", Integer.toString(RIM_WIDTH));
      node.b.put("border", FentMaterial.hex(FentMaterial.mix(0xFFFFFF, accent, 0.18f), FentMaterial.PANE_RIM));
      node.b.put("glass-border-bottom", FentMaterial.hex(0x000000, FentMaterial.PANE_RIM_BOTTOM));
      return saved;
   }

   // The base to accent gradient a pane is drawn over. It goes down before the pane's own glass fill,
   // because a glass node returns from the painter before the engine's gradient step is reached.
   public static void paneGradient(RenderBridge bridge, f node) {
      if (bridge == null || node == null || node.h < 1.0f || node.i < 1.0f) {
         return;
      }
      int base = FentAero.base();
      int accent = FentAero.accent();
      float[] r = corners(node);
      gradient(
         bridge, node.f, node.g, node.h, node.i, r[0], r[1], r[2], r[3],
         FentMaterial.argb(FentMaterial.PANE_GRADIENT_TOP, FentMaterial.lighten(base, 0.22f)),
         FentMaterial.argb(FentMaterial.PANE_GRADIENT_BOTTOM, accent)
      );
   }

   // The drop shadow under a menu pane. The node painter returns before its own shadow step once a
   // node is glass, and a pane with no shadow is the part that reads as a printed rectangle rather
   // than a sheet floating over the backdrop.
   public static void paneShadow(RenderBridge bridge, f node) {
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

   public static void restore(f node, String[] saved, String[] keys) {
      if (node == null || saved == null) {
         return;
      }
      for (int index = 0; index < keys.length && index < saved.length; ++index) {
         if (saved[index] == null) {
            node.b.remove(keys[index]);
         } else {
            node.b.put(keys[index], saved[index]);
         }
      }
   }

   // ----------------------------------------------------------------------------------------------
   // Surfaces
   // ----------------------------------------------------------------------------------------------

   // Writes the gradient a surface is filled with, derived from its own colour and the theme's
   // accent. Returns the values to put back when the node is done, or null when nothing was written.
   public static String[] surface(f node, boolean hover) {
      if (node == null) {
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
      String[] saved = save(node, SURFACE_KEYS);
      node.b.put("gradient-from", FentMaterial.hex(topRgb, topAlpha));
      node.b.put("gradient-to", FentMaterial.hex(bottomRgb, bottomAlpha));
      node.b.put("gradient-angle", Float.toString(FentMaterial.SURFACE_ANGLE));
      return saved;
   }

   public static String[] surfaceKeys() {
      return SURFACE_KEYS;
   }

   public static String[] paneKeys() {
      return PANE_KEYS;
   }

   private static String[] save(f node, String[] keys) {
      String[] saved = new String[keys.length];
      for (int index = 0; index < keys.length; ++index) {
         saved[index] = node.b.get(keys[index]);
      }
      return saved;
   }

   // ----------------------------------------------------------------------------------------------
   // The HUD
   // ----------------------------------------------------------------------------------------------

   // The in-game material for a module's own plate. The plate keeps the colour the user configured and
   // loses part of its alpha, so the world shows through where before it was a solid bar; the sheen
   // and the hairline go on afterwards, because they belong to the surface rather than to how see
   // through it is. A module that fades in or out brings the strength with it, since the overlay
   // follows the plate's alpha.
   public static void plate(RenderBridge bridge, float x, float y, float w, float h,
                            float r0, float r1, float r2, float r3, int plate) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      boolean material = FentAero.hudGlass() && !net.silentclient.internal.V.c.g;
      if (!material) {
         draw(bridge, x, y, w, h, r0, r1, r2, r3, plate);
         return;
      }
      int colour = plate;
      int alpha = FentMaterial.alphaOf(plate);
      if (alpha > 0) {
         // A real backdrop behind the widget - the framebuffer, blurred - is worth more transparency
         // than a plain plate, so the two are softened to different floors.
         FentRefraction.Backdrop backdrop = FentAero.refraction()
            ? FentRefraction.capture(bridge, x, y, w, h, r0, r1, r2, r3)
            : null;
         if (backdrop != null) {
            FentRefraction.draw(bridge, backdrop, x, y, w, h);
            colour = FentMaterial.soften(plate, FentMaterial.BACKDROP_PLATE_ALPHA, FentMaterial.BACKDROP_PLATE_FLOOR);
         } else {
            colour = FentMaterial.soften(plate, FentMaterial.PLATE_ALPHA, FentMaterial.PLATE_FLOOR);
         }
      }
      draw(bridge, x, y, w, h, r0, r1, r2, r3, colour);
      hud(bridge, x, y, w, h, r0, r1, r2, r3, plate);
   }

   // A plate that is its own body: a Keystrokes key, a Mouse Strokes cell. Same material, a shade
   // softer, plus the gradient the menu surfaces get, because a screen of tiles is the one place in
   // the HUD where a flat fill is most obvious.
   public static void tile(RenderBridge bridge, float x, float y, float w, float h, float radius, int colour) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      if (!FentAero.hudGlass() || net.silentclient.internal.V.c.g) {
         draw(bridge, x, y, w, h, radius, radius, radius, radius, colour);
         return;
      }
      int alpha = FentMaterial.alphaOf(colour);
      int plate = FentMaterial.soften(colour, FentMaterial.TILE_ALPHA, FentMaterial.TILE_FLOOR);
      draw(bridge, x, y, w, h, radius, radius, radius, radius, plate);
      if (alpha > 0 && h >= 3.0f) {
         float amount = Math.min(1.0f, alpha / 255.0f);
         int accent = FentAero.accent();
         boolean lightPlate = FentMaterial.luminance(FentMaterial.rgbOf(colour)) > 0.62f;
         gradient(
            bridge, x, y, w, h, radius, radius, radius, radius,
            FentMaterial.argb(Math.round(FentMaterial.TILE_GRADIENT_TOP * amount), FentMaterial.mix(FentMaterial.rgbOf(plate), 0xFFFFFF, 0.22f)),
            FentMaterial.argb(Math.round(FentMaterial.TILE_GRADIENT_TOP * 0.4f * amount), FentMaterial.mix(FentMaterial.rgbOf(plate), accent, 0.5f))
         );
         int rim = lightPlate
            ? FentMaterial.argb(Math.round(FentMaterial.TILE_RIM * amount), FentMaterial.mix(0x000000, accent, 0.18f))
            : FentMaterial.argb(Math.round(FentMaterial.TILE_RIM * amount), FentMaterial.mix(0xFFFFFF, accent, 0.22f));
         if ((rim >>> 24 & 0xFF) > 0) {
            bridge.drawOutline(x, y, w, h, radius, RIM_WIDTH, rim);
         }
      }
   }

   // The sheen and the rim over a plate the module already painted: the plate keeps its own colour and
   // alpha, so a widget that is meant to be read over the world still is. What is added is the lit top
   // edge and the hairline, which is what says glass. Strength follows the plate's alpha, so a module
   // fading in or out brings its glass with it, and a plate that is itself light gets a darker edge
   // instead of a white one that would not show against it.
   private static void hud(RenderBridge bridge, float x, float y, float w, float h,
                           float r0, float r1, float r2, float r3, int plate) {
      if (bridge == null || w < 4.0f || h < 4.0f) {
         return;
      }
      float amount = (plate >>> 24 & 0xFF) / 255.0f;
      if (amount <= 0.02f) {
         return;
      }
      int accent = FentAero.accent();
      boolean lightPlate = FentMaterial.luminance(FentMaterial.rgbOf(plate)) > 0.62f;
      int rim = lightPlate
         ? FentMaterial.argb(Math.round(0x3D * amount), FentMaterial.mix(0x000000, accent, 0.18f))
         : FentMaterial.argb(Math.round(0x4A * amount), FentMaterial.mix(0xFFFFFF, accent, 0.22f));
      if ((rim >>> 24 & 0xFF) == 0) {
         return;
      }
      try {
         net.silentclient.internal.x.e.a(
            bridge, x, y, w, h, r0, r1, r2, r3,
            0, RIM_WIDTH, rim, rim, FentMaterial.PLATE_SHEEN * amount
         );
      } catch (Throwable throwable) {
         // The engine's material painter moved; the plate stays as the module drew it.
      }
   }

   // ----------------------------------------------------------------------------------------------
   // Shared drawing
   // ----------------------------------------------------------------------------------------------

   // A base to accent gradient, rounded to the node's own corners. The engine has the painter for it,
   // which is why the menu surfaces are graded by attributes and not by hand.
   private static void gradient(RenderBridge bridge, float x, float y, float w, float h,
                                float r0, float r1, float r2, float r3, int from, int to) {
      if (bridge == null || w < 1.0f || h < 1.0f) {
         return;
      }
      net.silentclient.internal.x.a.a(bridge, bridge.deviceScale(), x, y, w, h, r0, r1, r2, r3, from, to, true);
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

   // Largest corner radius the node asks for, after the token scale has been resolved.
   private static float radius(f node) {
      return Math.max(
         node.c("rounded", 0.0f),
         Math.max(
            Math.max(node.c("rounded-tl", 0.0f), node.c("rounded-tr", 0.0f)),
            Math.max(node.c("rounded-br", 0.0f), node.c("rounded-bl", 0.0f))
         )
      );
   }

   private static float[] corners(f node) {
      float all = node.c("rounded", 0.0f);
      return new float[] {
         node.c("rounded-tl", all),
         node.c("rounded-tr", all),
         node.c("rounded-br", all),
         node.c("rounded-bl", all)
      };
   }      private FentGlass() {
   }
}
