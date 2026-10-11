package net.silentclient.fent;

// The Aero material in one place: the alpha every surface is drawn with, the numbers the glass and
// the HUD plates use, and the colour maths behind them.
//
// Two things moved in here. The colour helpers (mix, lighten, darken, luminance, argb, hex) used to
// be copied into both the theme and the glass painter, which is how the two drifted apart; the
// palette and the surfaces now share one implementation. And every alpha, sheen strength and floor
// now lives in a named constant, so "make it more transparent" is a number here instead of a hunt
// through three classes.
//
// The scale is deliberate. Menu surfaces are far more transparent than they were: a pane is a sheet
// over the client's own blurred menu backdrop, so a low fill costs nothing in legibility, and cards
// keep their own faint wash and gain a gradient rather than an opaque plate. The HUD is held back,
// because a widget is read over the world and not over a blur: its plate keeps enough body to stay
// readable, and only the edge and the light are added.
public final class FentMaterial {
   // ----------------------------------------------------------------------------------------------
   // Menu panes: a window or a dialog. The engine's glass path fills a pane with the node's own
   // background colour, which for the shipped themes is an almost opaque surface token - that is
   // what used to hide the backdrop. Aero answers with an explicit, much lower fill instead, and the
   // base to accent gradient underneath it carries the colour.
   // ----------------------------------------------------------------------------------------------
   public static final int PANE_FILL = 0x1C;
   public static final int PANE_FILL_HOVER = 0x26;
   public static final int PANE_GRADIENT_TOP = 0x24;
   public static final int PANE_GRADIENT_BOTTOM = 0x14;
   public static final float PANE_SHEEN = 0.9f;
   public static final int PANE_RIM = 0x4E;
   public static final int PANE_RIM_BOTTOM = 0x38;
   public static final int PANE_SHADOW_LIGHT = 0x2E;
   public static final int PANE_SHADOW_DARK = 0x46;
   public static final float PANE_SHADOW_BLUR = 10.0f;
   public static final float PANE_SHADOW_DROP = 3.0f;

   // A surface: every other plate in the menu - cards, rows, wells, fields, pills. It keeps its own
   // fill and its own hover colour, and gains the gradient: the top stop lifts its colour, the
   // bottom stop pulls it toward the accent, so a list of rows reads as one lit sheet instead of a
   // row of flat bars.
   public static final float SURFACE_LIFT = 0.30f;
   public static final float SURFACE_ACCENT = 0.42f;
   public static final float SURFACE_TOP_ALPHA = 1.0f;
   public static final float SURFACE_BOTTOM_ALPHA = 0.78f;
   public static final float SURFACE_ANGLE = 90.0f;

   // ----------------------------------------------------------------------------------------------
   // The in-game HUD. The plate is the module's own colour: only the alpha moves, and only down to
   // the floor, so a widget the user already made transparent is left alone.
   // ----------------------------------------------------------------------------------------------
   public static final float PLATE_ALPHA = 0.76f;
   public static final int PLATE_FLOOR = 116;
   public static final float PLATE_SHEEN = 0.62f;

   // A key or a cell that is its own plate (Keystrokes, Mouse Strokes): the same treatment, a shade
   // softer, because those are many small tiles rather than one body.
   public static final float TILE_ALPHA = 0.7f;
   public static final int TILE_FLOOR = 92;
   public static final int TILE_GRADIENT_TOP = 0x1C;
   public static final int TILE_RIM = 0x38;

   // The plate over a real blurred backdrop, like the island's: the backdrop is already a picture of
   // what is behind, so the plate goes lighter still and the world shows through it.
   public static final float BACKDROP_PLATE_ALPHA = 0.62f;
   public static final int BACKDROP_PLATE_FLOOR = 132;

   public static int argb(int alpha, int rgb) {
      return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
   }

   public static int rgbOf(int colour) {
      return colour & 0xFFFFFF;
   }

   public static int alphaOf(int colour) {
      return colour >>> 24 & 0xFF;
   }

   public static int withAlpha(int colour, int alpha) {
      return (alpha & 0xFF) << 24 | colour & 0xFFFFFF;
   }

   // A plate alpha scaled by a factor, never below the floor unless the user's own alpha was lower:
   // a widget that is already see through keeps what it has instead of being pushed opaque again.
   public static int soften(int colour, float factor, int floor) {
      int alpha = alphaOf(colour);
      if (alpha == 0) {
         return colour;
      }
      int softened = Math.round(alpha * factor);
      if (softened < floor) {
         softened = Math.min(alpha, floor);
      }
      return withAlpha(colour, softened);
   }

   // RGB only, the way the theme tokens are written; alphas are set by the caller.
   public static int mix(int from, int to, float amount) {
      float t = amount < 0.0f ? 0.0f : (amount > 1.0f ? 1.0f : amount);
      int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
      return red << 16 | green << 8 | blue;
   }

   public static int lighten(int rgb, float amount) {
      return mix(rgb, 0xFFFFFF, amount);
   }

   public static int darken(int rgb, float amount) {
      return mix(rgb, 0x000000, amount);
   }

   public static float luminance(int rgb) {
      return (0.299f * (rgb >> 16 & 0xFF) + 0.587f * (rgb >> 8 & 0xFF) + 0.114f * (rgb & 0xFF)) / 255.0f;
   }

   // #RRGGBBAA, the order the node painter reads.
   public static String hex(int colour, int alpha) {
      return String.format("#%06X%02X", Integer.valueOf(colour & 0xFFFFFF), Integer.valueOf(alpha & 0xFF));
   }

   private FentMaterial() {
   }
}
