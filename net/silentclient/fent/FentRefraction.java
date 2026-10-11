package net.silentclient.fent;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.silentclient.common.api.bridge.RenderBridge;

// Real refraction for the Aero glass: a widget samples the frame behind it, blurs it, bends it the way
// a sheet of glass does, and the result is drawn under the widget's own translucent plate.
//
// Nothing in the client can do this. SilentClient's only backdrop blur is Minecraft's own post effect,
// which runs over the whole screen - that is why the menu has one and a HUD widget cannot - and the
// bridge's own drawBackdropBlur is a darkening wash rather than a blur. So the pixels come straight
// from the framebuffer through LWJGL's glReadPixels, reached by reflection so the build keeps no
// native library on its classpath, and everything after that is small CPU work: box downscale, two box
// passes, then a displacement that grows toward the edges, which is what bends the world around the
// widget's silhouette.
//
// The readback is the cost, so backdrops are pooled instead of one widget owning the feature: a few
// widgets are sampled per frame, each at most every other frame, and each one has its own size cap. A
// widget that does not get a backdrop is not broken - it keeps the plain plate with the material's
// transparency, which is what every widget had before. Anything that goes wrong - no GL, a read that
// comes back empty, a grid that does not fit - turns the whole thing off for the session.
public final class FentRefraction {
   // GL_RGBA and GL_UNSIGNED_BYTE from the OpenGL constants, so no LWJGL type is needed to compile.
   private static final int GL_RGBA = 0x1908;
   private static final int GL_UNSIGNED_BYTE = 0x1401;

   // The grid the blurred backdrop lives on. Nine cells of height is enough for a pill: the corner
   // mask and the bend are both smoother than the blur, so resolution past this buys nothing.
   private static final int GRID_WIDTH = 96;
   private static final int MIN_HEIGHT = 8;
   private static final int MAX_HEIGHT = 48;
   // 600x400 physical pixels. A widget island is a fifth of that; a full size panel is not, and a
   // panel that big keeps the plain plate rather than paying for a readback.
   private static final int MAX_PIXELS = 240000;
   private static final int REFRESH_EVERY = 2;
   // Three backdrops live at once, so the readbacks stay bounded no matter how many widgets are on
   // screen: the pool evicts whichever has gone longest without being drawn.
   private static final int MAX_BACKDROPS = 3;
   // A readback stalls the pipeline, so how many of them a moment can contain is capped as well: two
   // every dozen milliseconds is a couple per frame at sixty. A widget the budget cannot reach keeps
   // the backdrop it already has for a few frames - a still widget does not care - and past that it
   // falls back to the plain plate, which is what it had before this existed.
   private static final int MAX_READS = 2;
   private static final long READ_WINDOW_NANOS = 12000000L;
   private static final int STALE_LIMIT = 8;

   // How far outside the widget the bend is allowed to reach, as a fraction of its size, and how far
   // the outermost cells are displaced, in cells.
   private static final float MARGIN = 0.12f;
   private static final float BEND = 2.6f;

   private static final int[] EMPTY = new int[0];

   private static Method readPixels;
   private static boolean resolved;
   private static boolean broken;
   private static boolean warned;

   private static final List<Backdrop> POOL = new ArrayList<Backdrop>();
   private static ByteBuffer buffer;
   // Scratch for one capture: both are only touched between a readback and the grid it produces, so
   // the backdrops do not each need to hold a full size copy of the frame.
   private static int[] source = EMPTY;
   private static int[] raw = EMPTY;
   private static long frames;
   private static long windowStartedAt;
   private static int readsInWindow;

   // One widget's blurred backdrop: the grid that gets drawn, and the rect it came from.
   public static final class Backdrop {
      private final Object key = new Object();
      private int[] grid = EMPTY;
      private int width;
      private int height;
      private int version;
      private int rectX;
      private int rectY;
      private int rectW;
      private int rectH;
      private long sampledFrame;
      private long usedFrame;
   }

   // Called from a widget's plate, once per frame, before the plate goes down. Returns the backdrop to
   // draw under it, or null when this widget cannot have one this frame.
   public static Backdrop capture(RenderBridge bridge, float x, float y, float w, float h,
                                  float r0, float r1, float r2, float r3) {
      if (broken || bridge == null || w < 24.0f || h < 12.0f) {
         return null;
      }
      double scale = bridge.deviceScale();
      if (!(scale > 0.0)) {
         scale = 1.0;
      }
      int px = (int)Math.round(x * scale);
      int py = (int)Math.round((bridge.screenHeight() - y - h) * scale);
      int pw = (int)Math.round(w * scale);
      int ph = (int)Math.round(h * scale);
      ++frames;
      if (pw < 16 || ph < 8 || (long)pw * ph > MAX_PIXELS) {
         return null;
      }
      Backdrop backdrop = obtain(px, py, pw, ph);
      backdrop.usedFrame = frames;
      // The widget usually sits still: keep the last backdrop, and only pay for a new read when the
      // rect moved or a couple of frames have gone by, which is what keeps a moving scene current
      // without a stall every frame.
      if (backdrop.grid.length > 0 && frames - backdrop.sampledFrame < REFRESH_EVERY) {
         return backdrop;
      }
      if (!resolve()) {
         return null;
      }
      if (!affordable()) {
         // Nothing to read with: the freshest picture this widget already has, or the plain plate.
         boolean usable = backdrop.grid.length > 0 && frames - backdrop.sampledFrame <= STALE_LIMIT;
         return usable ? backdrop : null;
      }
      try {
         int size = pw * ph * 4;
         if (buffer == null || buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size);
         }
         buffer.clear();
         buffer.limit(size);
         readPixels.invoke(null, px, py, pw, ph, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
         source = ensure(source, pw * ph);
         if (!toArgb(buffer, source, pw, ph)) {
            return null;
         }
         store(backdrop, source, pw, ph, px, py, w, h, r0, r1, r2, r3);
         return backdrop;
      } catch (Throwable throwable) {
         broken = true;
         if (!warned) {
            warned = true;
            System.err.println("[Fent] the framebuffer readback for glass refraction is not working, "
               + "the widgets keep the plain plate: " + throwable);
         }
         return null;
      }
   }

   // Whether this moment's readback budget still has room.
   private static boolean affordable() {
      long now = System.nanoTime();
      if (now - windowStartedAt >= READ_WINDOW_NANOS || now < windowStartedAt) {
         windowStartedAt = now;
         readsInWindow = 0;
      }
      if (readsInWindow >= MAX_READS) {
         return false;
      }
      ++readsInWindow;
      return true;
   }

   // Draws a backdrop at the widget's own rect.
   public static void draw(RenderBridge bridge, Backdrop backdrop, float x, float y, float w, float h) {
      if (bridge == null || backdrop == null || backdrop.grid.length == 0 || backdrop.width <= 0 || backdrop.height <= 0) {
         return;
      }
      bridge.drawLiveImage(
         backdrop.key, backdrop.grid, backdrop.width, backdrop.height, backdrop.version, x, y, w, h, -1
      );
   }

   // The backdrop for a rect, reused while the rect is unchanged so a widget that has not moved keeps
   // its grid instead of reading the framebuffer again.
   private static Backdrop obtain(int px, int py, int pw, int ph) {
      Backdrop oldest = null;
      for (Backdrop candidate : POOL) {
         if (candidate.rectX == px && candidate.rectY == py && candidate.rectW == pw && candidate.rectH == ph) {
            return candidate;
         }
         if (oldest == null || candidate.usedFrame < oldest.usedFrame) {
            oldest = candidate;
         }
      }
      if (POOL.size() < MAX_BACKDROPS) {
         Backdrop created = new Backdrop();
         POOL.add(created);
         return created;
      }
      oldest.grid = EMPTY;
      return oldest;
   }

   // Installs a backdrop from pixels: the readback path calls this. rectX and rectY are the physical
   // framebuffer position the pixels came from, and keep a stationary widget from paying for a new read.
   private static void store(Backdrop backdrop, int[] pixels, int width, int height, int rectX, int rectY,
                             float rectWidth, float rectHeight, float r0, float r1, float r2, float r3) {
      if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
         return;
      }
      backdrop.rectX = rectX;
      backdrop.rectY = rectY;
      backdrop.rectW = width;
      backdrop.rectH = height;
      backdrop.sampledFrame = frames;
      build(backdrop, pixels, width, height, rectWidth, rectHeight, r0, r1, r2, r3);
      ++backdrop.version;
   }

   // ----------------------------------------------------------------------------------------------
   // The pipeline past the readback, all of it plain arrays.
   // ----------------------------------------------------------------------------------------------

   private static void build(Backdrop backdrop, int[] pixels, int pw, int ph, float w, float h,
                             float r0, float r1, float r2, float r3) {
      // Square cells both ways: the grid keeps the widget's shape, so the corner radii stay circular
      // and the blur stays the same width horizontally and vertically. The caps are applied by
      // rescaling both axes together, never one on its own.
      int cellsW = Math.min(GRID_WIDTH, pw);
      int cellsH = Math.round((float)cellsW * ph / pw);
      if (cellsH > MAX_HEIGHT) {
         cellsH = MAX_HEIGHT;
         cellsW = Math.max(2, Math.round((float)cellsH * pw / ph));
      } else if (cellsH < MIN_HEIGHT) {
         cellsH = MIN_HEIGHT;
         cellsW = Math.max(2, Math.round((float)cellsH * ph / pw));
      }
      // Physical pixels per cell, so the grid keeps the widget's shape and the corner radii stay
      // circular instead of going elliptical.
      float cell = (float)pw / cellsW;
      int marginX = Math.max(1, Math.round(pw * MARGIN / cell));
      int marginY = Math.max(1, Math.round(ph * MARGIN / cell));
      int cellsWide = cellsW + 2 * marginX;
      int cellsHigh = cellsH + 2 * marginY;
      raw = ensure(raw, cellsWide * cellsHigh);
      boxDownscale(pixels, pw, ph, cellsWide, cellsHigh, raw);
      boxBlur(raw, cellsWide, cellsHigh, Math.max(1, Math.min(cellsWide, cellsHigh) / 5));
      boxBlur(raw, cellsWide, cellsHigh, Math.max(1, Math.min(cellsWide, cellsHigh) / 5));
      backdrop.width = cellsW;
      backdrop.height = cellsH;
      backdrop.grid = ensure(backdrop.grid, cellsW * cellsH);
      bend(raw, cellsWide, cellsHigh, cellsW, cellsH, marginX, marginY, BEND, backdrop.grid);
      // The shape is cut into the pixels, so the image needs no clipping when it is drawn, and the
      // corners come out antialiased instead of stepped.
      float radius = Math.max(0.0f, Math.max(Math.max(r0, r1), Math.max(r2, r3))) * cellsW / w;
      maskCorners(backdrop.grid, cellsW, cellsH, radius, 0.6f);
   }

   // The framebuffer, bottom up, to ARGB top down. False when the read produced nothing at all,
   // which is what a readback that did not happen looks like.
   public static boolean toArgb(ByteBuffer bytes, int[] out, int width, int height) {
      if (bytes == null || out == null || width <= 0 || height <= 0) {
         return false;
      }
      bytes.position(0);
      int limit = Math.min(bytes.limit(), width * height * 4);
      int rows = limit / (width * 4);
      if (rows <= 0) {
         return false;
      }
      boolean any = false;
      for (int row = 0; row < rows; ++row) {
         int target = (height - 1 - row) * width;
         int from = row * width * 4;
         for (int column = 0; column < width; ++column) {
            int at = from + column * 4;
            int red = bytes.get(at) & 0xFF;
            int green = bytes.get(at + 1) & 0xFF;
            int blue = bytes.get(at + 2) & 0xFF;
            int alpha = bytes.get(at + 3) & 0xFF;
            if (red != 0 || green != 0 || blue != 0) {
               any = true;
            }
            out[target + column] = alpha << 24 | red << 16 | green << 8 | blue;
         }
      }
      return any;
   }

   // Box average of the whole source into a smaller grid: the first and largest part of the blur.
   public static void boxDownscale(int[] src, int sw, int sh, int dw, int dh, int[] out) {
      for (int oy = 0; oy < dh; ++oy) {
         int y0 = (int)((long)oy * sh / dh);
         int y1 = (int)((long)(oy + 1) * sh / dh);
         if (y1 <= y0) {
            y1 = y0 + 1;
         }
         for (int ox = 0; ox < dw; ++ox) {
            int x0 = (int)((long)ox * sw / dw);
            int x1 = (int)((long)(ox + 1) * sw / dw);
            if (x1 <= x0) {
               x1 = x0 + 1;
            }
            long a = 0L;
            long r = 0L;
            long g = 0L;
            long b = 0L;
            int count = 0;
            for (int y = y0; y < y1 && y < sh; ++y) {
               int row = y * sw;
               for (int x = x0; x < x1 && x < sw; ++x) {
                  int pixel = src[row + x];
                  a += pixel >>> 24 & 0xFF;
                  r += pixel >> 16 & 0xFF;
                  g += pixel >> 8 & 0xFF;
                  b += pixel & 0xFF;
                  ++count;
               }
            }
            if (count == 0) {
               out[oy * dw + ox] = 0;
               continue;
            }
            out[oy * dw + ox] = (int)(a / count) << 24 | (int)(r / count) << 16
               | (int)(g / count) << 8 | (int)(b / count);
         }
      }
   }

   // A separable box blur, run twice: two passes of a box approximate a gaussian closely enough that
   // the difference is invisible once the backdrop is this soft.
   public static void boxBlur(int[] pixels, int width, int height, int radius) {
      if (radius <= 0 || pixels.length < width * height) {
         return;
      }
      int[] line = new int[Math.max(width, height)];
      for (int y = 0; y < height; ++y) {
         int row = y * width;
         for (int x = 0; x < width; ++x) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            int count = 0;
            for (int k = x - radius; k <= x + radius; ++k) {
               int at = k < 0 ? 0 : (k >= width ? width - 1 : k);
               int pixel = pixels[row + at];
               a += pixel >>> 24 & 0xFF;
               r += pixel >> 16 & 0xFF;
               g += pixel >> 8 & 0xFF;
               b += pixel & 0xFF;
               ++count;
            }
            line[x] = (a / count) << 24 | (r / count) << 16 | (g / count) << 8 | b / count;
         }
         System.arraycopy(line, 0, pixels, row, width);
      }
      for (int x = 0; x < width; ++x) {
         for (int y = 0; y < height; ++y) {
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            int count = 0;
            for (int k = y - radius; k <= y + radius; ++k) {
               int at = k < 0 ? 0 : (k >= height ? height - 1 : k);
               int pixel = pixels[at * width + x];
               a += pixel >>> 24 & 0xFF;
               r += pixel >> 16 & 0xFF;
               g += pixel >> 8 & 0xFF;
               b += pixel & 0xFF;
               ++count;
            }
            line[y] = (a / count) << 24 | (r / count) << 16 | (g / count) << 8 | b / count;
         }
         for (int y = 0; y < height; ++y) {
            pixels[y * width + x] = line[y];
         }
      }
   }

   // The bend. Each output cell covers one cell of the source, offset from origin by the margin, and
   // is displaced away from the middle by amount * (how far out it is)^3: nothing at the centre, the
   // full pull at the rim, where the glass is thickest. That is what carries the world just outside
   // the widget into its edge and makes the edge read as a lens.
   public static void bend(int[] src, int sw, int sh, int dw, int dh, int originX, int originY, float amount, int[] out) {
      for (int oy = 0; oy < dh; ++oy) {
         float ny = (oy + 0.5f) / dh * 2.0f - 1.0f;
         float away = ny * ny * ny * amount;
         float sy = originY + oy + 0.5f + away;
         for (int ox = 0; ox < dw; ++ox) {
            float nx = (ox + 0.5f) / dw * 2.0f - 1.0f;
            float ax = nx * nx * nx * amount;
            float sx = originX + ox + 0.5f + ax;
            out[oy * dw + ox] = sample(src, sw, sh, sx, sy);
         }
      }
   }

   // Bilinear, clamped, so the bend never reads outside the frame.
   private static int sample(int[] src, int width, int height, float x, float y) {
      float cx = x - 0.5f;
      float cy = y - 0.5f;
      int cellX = (int)Math.floor(cx);
      int cellY = (int)Math.floor(cy);
      // The fractions come off the unclamped cell, so a sample far outside the grid still lands on
      // the edge it is nearest instead of folding back inside it.
      float fx = cx - cellX;
      float fy = cy - cellY;
      int x0 = clamp(cellX, width);
      int x1 = clamp(cellX + 1, width);
      int y0 = clamp(cellY, height);
      int y1 = clamp(cellY + 1, height);
      int a = src[y0 * width + x0];
      int b = src[y0 * width + x1];
      int c = src[y1 * width + x0];
      int d = src[y1 * width + x1];
      int top = mix(a, b, fx);
      int bottom = mix(c, d, fx);
      return mix(top, bottom, fy);
   }

   private static int mix(int from, int to, float amount) {
      int a = Math.round((from >>> 24 & 0xFF) + ((to >>> 24 & 0xFF) - (from >>> 24 & 0xFF)) * amount);
      int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * amount);
      int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * amount);
      int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
      return clamp(a) << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b);
   }

   private static int clamp(int value, int limit) {
      return value < 0 ? 0 : (value >= limit ? limit - 1 : value);
   }

   private static int clamp(int value) {
      return value < 0 ? 0 : (value > 255 ? 255 : value);
   }

   // Cuts the widget's shape into the alpha, so the image needs no clipping when it is drawn and the
   // rounded ends come out antialiased. radius is in cells, and inset keeps the picture a hair
   // inside the silhouette so no unblurred world shows through the edge.
   public static void maskCorners(int[] pixels, int width, int height, float radius, float inset) {
      if (pixels.length < width * height) {
         return;
      }
      float half = Math.min(width, height) / 2.0f;
      float r = Math.max(0.0f, Math.min(radius, half)) + inset;
      float left = r;
      float right = width - r;
      float top = r;
      float bottom = height - r;
      for (int y = 0; y < height; ++y) {
         float cy = y + 0.5f;
         for (int x = 0; x < width; ++x) {
            float cx = x + 0.5f;
            float dx = cx < left ? left - cx : (cx > right ? cx - right : 0.0f);
            float dy = cy < top ? top - cy : (cy > bottom ? cy - bottom : 0.0f);
            float coverage;
            if (r <= 0.0f) {
               coverage = 1.0f;
            } else {
               float distance = (float)Math.sqrt(dx * dx + dy * dy) - r;
               coverage = 0.5f - distance;
               coverage = coverage < 0.0f ? 0.0f : (coverage > 1.0f ? 1.0f : coverage);
            }
            int at = y * width + x;
            int alpha = Math.round((pixels[at] >>> 24 & 0xFF) * coverage);
            pixels[at] = alpha << 24 | pixels[at] & 0xFFFFFF;
         }
      }
   }

   private static boolean resolve() {
      if (resolved) {
         return readPixels != null;
      }
      resolved = true;
      try {
         Class<?> gl = Class.forName("org.lwjgl.opengl.GL11");
         readPixels = gl.getMethod("glReadPixels", int.class, int.class, int.class, int.class,
            int.class, int.class, ByteBuffer.class);
      } catch (Throwable throwable) {
         readPixels = null;
         System.err.println("[Fent] no framebuffer readback available for glass refraction, "
            + "the widgets keep the plain plate: " + throwable);
      }
      return readPixels != null;
   }

   private static int[] ensure(int[] array, int size) {
      return array.length >= size ? array : new int[size];
   }

   private FentRefraction() {
   }
}
