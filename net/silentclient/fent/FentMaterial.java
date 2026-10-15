package net.silentclient.fent;

public final class FentMaterial {
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

   public static final float SURFACE_LIFT = 0.18f;
   public static final float SURFACE_ACCENT = 0.2f;
   public static final float SURFACE_TOP_ALPHA = 1.0f;
   public static final float SURFACE_BOTTOM_ALPHA = 0.82f;
   public static final float SURFACE_ANGLE = 90.0f;

   public static final float PLATE_ALPHA = 0.76f;
   public static final int PLATE_FLOOR = 116;
   public static final float PLATE_SHEEN = 0.62f;

   public static final float TILE_ALPHA = 0.7f;
   public static final int TILE_FLOOR = 92;
   public static final int TILE_SHEEN = 0x1C;
   public static final int TILE_RIM = 0x38;

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

   public static float saturation(int rgb) {
      int red = rgb >> 16 & 0xFF;
      int green = rgb >> 8 & 0xFF;
      int blue = rgb & 0xFF;
      int max = Math.max(red, Math.max(green, blue));
      if (max == 0) {
         return 0.0f;
      }
      int min = Math.min(red, Math.min(green, blue));
      return (float)(max - min) / (float)max;
   }

   private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

   public static String hex(int colour, int alpha) {
      char[] out = new char[9];
      out[0] = '#';
      int rgb = colour & 0xFFFFFF;
      for (int index = 0; index < 6; ++index) {
         out[1 + index] = HEX_DIGITS[rgb >>> (20 - index * 4) & 0xF];
      }
      int a = alpha & 0xFF;
      out[7] = HEX_DIGITS[a >>> 4 & 0xF];
      out[8] = HEX_DIGITS[a & 0xF];
      return new String(out);
   }

   private FentMaterial() {
   }
}
