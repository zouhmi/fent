package net.silentclient.fent;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.silentclient.common.api.bridge.RenderBridge;

public final class FentRefraction {
   private static final int GL_RGBA = 0x1908;
   private static final int GL_UNSIGNED_BYTE = 0x1401;

   private static final int GRID_WIDTH = 80;
   private static final int MIN_HEIGHT = 8;
   private static final int MAX_HEIGHT = 40;
   private static final int MAX_PIXELS = 180000;
   private static final int REFRESH_EVERY = 4;
   private static final int MAX_BACKDROPS = 3;
   private static final int MAX_READS = 1;
   private static final long READ_WINDOW_NANOS = 16000000L;
   private static final int STALE_LIMIT = 10;

   private static final float MARGIN = 0.10f;
   private static final float BEND = 2.0f;

   private static final int[] EMPTY = new int[0];

   private static Method readPixels;
   private static boolean resolved;
   private static boolean broken;
   private static boolean warned;

   private static final List<Backdrop> POOL = new ArrayList<Backdrop>();
   private static ByteBuffer buffer;
   private static int[] source = EMPTY;
   private static int[] raw = EMPTY;
   private static int[] blurLine = EMPTY;
   private static byte[] byteScratch = new byte[0];
   private static float[] bendScratch = new float[0];
   private static long frames;
   private static long lastFrameNanos;
   private static long windowStartedAt;
   private static int readsInWindow;

   public static final class Backdrop {      private final Object key = new Object();
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
      tickFrame();
      if (pw < 16 || ph < 8 || (long)pw * ph > MAX_PIXELS) {
         return null;
      }
      int marginX = Math.max(2, (int)Math.round(pw * MARGIN));
      int marginY = Math.max(2, (int)Math.round(ph * MARGIN));
      int screenW = (int)Math.round(bridge.screenWidth() * scale);
      int screenH = (int)Math.round(bridge.screenHeight() * scale);
      int readX = px - marginX;
      int readY = py - marginY;
      int readW = pw + 2 * marginX;
      int readH = ph + 2 * marginY;
      if (screenW > 0 && screenH > 0) {
         readX = Math.max(0, readX);
         readY = Math.max(0, readY);
         readW = Math.min(screenW, px + pw + marginX) - readX;
         readH = Math.min(screenH, py + ph + marginY) - readY;
      }
      if (readW < 16 || readH < 8 || (long)readW * readH > MAX_PIXELS + MAX_PIXELS / 4) {
         return null;
      }
      Backdrop backdrop = find(readX, readY, readW, readH);
      if (backdrop != null) {
         backdrop.usedFrame = frames;
         if (backdrop.grid.length > 0 && frames - backdrop.sampledFrame < REFRESH_EVERY) {
            return backdrop;
         }
      }
      if (!resolve()) {
         return null;
      }
      if (!affordable()) {
         boolean usable = backdrop != null && backdrop.grid.length > 0
            && frames - backdrop.sampledFrame <= STALE_LIMIT;
         return usable ? backdrop : null;
      }
      if (backdrop == null) {
         backdrop = create(readX, readY, readW, readH);
      }
      backdrop.usedFrame = frames;
      try {
         int size = readW * readH * 4;
         if (buffer == null || buffer.capacity() < size) {
            buffer = ByteBuffer.allocateDirect(size);
         }
         buffer.clear();
         buffer.limit(size);
         readPixels.invoke(null, readX, readY, readW, readH, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
         source = ensure(source, readW * readH);
         if (!toArgb(buffer, source, readW, readH)) {
            return null;
         }
         store(backdrop, source, readW, readH, readX, readY, px - readX, py - readY,
            pw, ph, r0, r1, r2, r3, scale);
         return backdrop;
      } catch (Throwable throwable) {
         broken = true;
         if (!warned) {
            warned = true;
            System.err.println("[Fent] glass refraction is off, the widgets keep the plain plate: " + throwable);
         }
         return null;
      }
   }

   private static void tickFrame() {
      long now = System.nanoTime();
      if (now - lastFrameNanos >= 8000000L || now < lastFrameNanos || frames == 0L) {
         lastFrameNanos = now;
         ++frames;
      }
   }

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

   public static void draw(RenderBridge bridge, Backdrop backdrop, float x, float y, float w, float h) {
      if (bridge == null || backdrop == null || backdrop.grid.length == 0 || backdrop.width <= 0 || backdrop.height <= 0) {
         return;
      }
      bridge.drawLiveImage(
         backdrop.key, backdrop.grid, backdrop.width, backdrop.height, backdrop.version, x, y, w, h, -1
      );
   }

   private static Backdrop find(int px, int py, int pw, int ph) {
      for (Backdrop candidate : POOL) {
         if (candidate.rectX == px && candidate.rectY == py && candidate.rectW == pw && candidate.rectH == ph) {
            return candidate;
         }
      }
      return null;
   }

   private static Backdrop create(int px, int py, int pw, int ph) {
      if (POOL.size() < MAX_BACKDROPS) {
         Backdrop created = new Backdrop();
         created.rectX = px;
         created.rectY = py;
         created.rectW = pw;
         created.rectH = ph;
         POOL.add(created);
         return created;
      }
      Backdrop oldest = null;
      for (Backdrop candidate : POOL) {
         if (oldest == null || candidate.usedFrame < oldest.usedFrame) {
            oldest = candidate;
         }
      }
      oldest.grid = EMPTY;
      oldest.width = 0;
      oldest.height = 0;
      oldest.sampledFrame = 0L;
      oldest.rectX = px;
      oldest.rectY = py;
      oldest.rectW = pw;
      oldest.rectH = ph;
      return oldest;
   }
   private static void store(Backdrop backdrop, int[] pixels, int width, int height, int rectX, int rectY,
                             int originX, int originY, int widgetW, int widgetH,
                             float r0, float r1, float r2, float r3, double scale) {
      if (pixels == null || width <= 0 || height <= 0 || pixels.length < width * height) {
         return;
      }
      backdrop.rectX = rectX;
      backdrop.rectY = rectY;
      backdrop.rectW = width;
      backdrop.rectH = height;
      backdrop.sampledFrame = frames;
      build(backdrop, pixels, width, height, originX, originY, widgetW, widgetH, r0, r1, r2, r3, scale);
      ++backdrop.version;
   }

   private static void build(Backdrop backdrop, int[] pixels, int sw, int sh,
                             int originX, int originY, int widgetW, int widgetH,
                             float r0, float r1, float r2, float r3, double scale) {
      int cellsW = Math.min(GRID_WIDTH, widgetW);
      int cellsH = Math.round((float)cellsW * widgetH / widgetW);
      if (cellsH > MAX_HEIGHT) {
         cellsH = MAX_HEIGHT;
         cellsW = Math.max(2, Math.round((float)cellsH * widgetW / widgetH));
      } else if (cellsH < MIN_HEIGHT) {
         cellsH = MIN_HEIGHT;
         cellsW = Math.max(2, Math.round((float)cellsH * widgetW / widgetH));
      }
      float cell = (float)widgetW / cellsW;
      int left = Math.max(0, (int)Math.round(originX / cell));
      int top = Math.max(0, (int)Math.round(originY / cell));
      int right = Math.max(0, (int)Math.round((sw - originX - widgetW) / cell));
      int bottom = Math.max(0, (int)Math.round((sh - originY - widgetH) / cell));
      int cellsWide = cellsW + left + right;
      int cellsHigh = cellsH + top + bottom;
      raw = ensure(raw, cellsWide * cellsHigh);
      boxDownscale(pixels, sw, sh, cellsWide, cellsHigh, raw);
      boxBlur(raw, cellsWide, cellsHigh, Math.max(1, Math.min(cellsWide, cellsHigh) / 5));
      boxBlur(raw, cellsWide, cellsHigh, Math.max(1, Math.min(cellsWide, cellsHigh) / 5));
      backdrop.width = cellsW;
      backdrop.height = cellsH;
      backdrop.grid = ensure(backdrop.grid, cellsW * cellsH);
      bend(raw, cellsWide, cellsHigh, cellsW, cellsH, left, top, BEND, backdrop.grid);
      float toCells = (float)scale / cell;
      maskCorners(backdrop.grid, cellsW, cellsH,
         r0 * toCells, r1 * toCells, r2 * toCells, r3 * toCells, 0.0f);
   }

   public static boolean toArgb(ByteBuffer bytes, int[] out, int width, int height) {
      if (bytes == null || out == null || width <= 0 || height <= 0) {
         return false;
      }
      bytes.position(0);
      int size = width * height * 4;
      int limit = Math.min(bytes.limit(), size);
      int rows = limit / (width * 4);
      if (rows != height) {
         return false;
      }
      // Local scratch: the old shared byteScratch raced when two callers
      // (HUD plates + MaterialCheck) ran on different threads.
      byte[] raw = new byte[size];
      bytes.get(raw, 0, size);
      for (int row = 0; row < rows; ++row) {
         int target = (height - 1 - row) * width;
         int from = row * width * 4;
         for (int column = 0; column < width; ++column) {
            int at = from + column * 4;
            int red = raw[at] & 0xFF;
            int green = raw[at + 1] & 0xFF;
            int blue = raw[at + 2] & 0xFF;
            int alpha = raw[at + 3] & 0xFF;
            out[target + column] = alpha << 24 | red << 16 | green << 8 | blue;
         }
      }
      return true;
   }

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

   public static void boxBlur(int[] pixels, int width, int height, int radius) {
      if (radius <= 0 || pixels.length < width * height) {
         return;
      }
      int[] line = blurLine = ensure(blurLine, Math.max(width, height));
      int div = 2 * radius + 1;
      long reciprocal = ((1L << 32) + div - 1) / div;
      for (int y = 0; y < height; ++y) {
         int row = y * width;
         long a = 0L;
         long r = 0L;
         long g = 0L;
         long b = 0L;
         for (int k = -radius; k <= radius; ++k) {
            int pixel = pixels[row + edge(k, width)];
            a += pixel >>> 24 & 0xFF;
            r += pixel >> 16 & 0xFF;
            g += pixel >> 8 & 0xFF;
            b += pixel & 0xFF;
         }
         for (int x = 0; x < width; ++x) {
            line[x] = (int)((a * reciprocal) >>> 32) << 24 | (int)((r * reciprocal) >>> 32) << 16
               | (int)((g * reciprocal) >>> 32) << 8 | (int)((b * reciprocal) >>> 32);
            int out = pixels[row + edge(x - radius, width)];
            int in = pixels[row + edge(x + radius + 1, width)];
            a += (in >>> 24 & 0xFF) - (out >>> 24 & 0xFF);
            r += (in >> 16 & 0xFF) - (out >> 16 & 0xFF);
            g += (in >> 8 & 0xFF) - (out >> 8 & 0xFF);
            b += (in & 0xFF) - (out & 0xFF);
         }
         System.arraycopy(line, 0, pixels, row, width);
      }
      for (int x = 0; x < width; ++x) {
         long a = 0L;
         long r = 0L;
         long g = 0L;
         long b = 0L;
         for (int k = -radius; k <= radius; ++k) {
            int pixel = pixels[edge(k, height) * width + x];
            a += pixel >>> 24 & 0xFF;
            r += pixel >> 16 & 0xFF;
            g += pixel >> 8 & 0xFF;
            b += pixel & 0xFF;
         }
         for (int y = 0; y < height; ++y) {
            line[y] = (int)((a * reciprocal) >>> 32) << 24 | (int)((r * reciprocal) >>> 32) << 16
               | (int)((g * reciprocal) >>> 32) << 8 | (int)((b * reciprocal) >>> 32);
            int out = pixels[edge(y - radius, height) * width + x];
            int in = pixels[edge(y + radius + 1, height) * width + x];
            a += (in >>> 24 & 0xFF) - (out >>> 24 & 0xFF);
            r += (in >> 16 & 0xFF) - (out >> 16 & 0xFF);
            g += (in >> 8 & 0xFF) - (out >> 8 & 0xFF);
            b += (in & 0xFF) - (out & 0xFF);
         }
         for (int y = 0; y < height; ++y) {
            pixels[y * width + x] = line[y];
         }
      }
   }

   private static int edge(int at, int limit) {
      return at < 0 ? 0 : (at >= limit ? limit - 1 : at);
   }

   public static void bend(int[] src, int sw, int sh, int dw, int dh, int originX, int originY, float amount, int[] out) {
      float[] scratch = bendScratch = ensureF(bendScratch, dw + dh);
      for (int oy = 0; oy < dh; ++oy) {
         float ny = (oy + 0.5f) / dh * 2.0f - 1.0f;
         scratch[oy] = originY + oy + 0.5f + ny * ny * ny * amount;
      }
      for (int ox = 0; ox < dw; ++ox) {
         float nx = (ox + 0.5f) / dw * 2.0f - 1.0f;
         scratch[dh + ox] = originX + ox + 0.5f + nx * nx * nx * amount;
      }
      for (int oy = 0; oy < dh; ++oy) {
         float sy = scratch[oy];
         int row = oy * dw;
         for (int ox = 0; ox < dw; ++ox) {
            out[row + ox] = sample(src, sw, sh, scratch[dh + ox], sy);
         }
      }
   }

   private static int sample(int[] src, int width, int height, float x, float y) {
      float cx = x - 0.5f;
      float cy = y - 0.5f;
      int cellX = (int)Math.floor(cx);
      int cellY = (int)Math.floor(cy);
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

   public static void maskCorners(int[] pixels, int width, int height,
                                  float topLeft, float topRight, float bottomRight, float bottomLeft,
                                  float inset) {
      if (pixels.length < width * height) {
         return;
      }
      if (topLeft <= 0.0f && topRight <= 0.0f && bottomRight <= 0.0f && bottomLeft <= 0.0f) {
         return;
      }
      float half = Math.min(width, height) / 2.0f;
      float rtl = Math.max(0.0f, Math.min(topLeft, half)) + inset;
      float rtr = Math.max(0.0f, Math.min(topRight, half)) + inset;
      float rbr = Math.max(0.0f, Math.min(bottomRight, half)) + inset;
      float rbl = Math.max(0.0f, Math.min(bottomLeft, half)) + inset;
      float halfW = width / 2.0f;
      float halfH = height / 2.0f;
      for (int y = 0; y < height; ++y) {
         float cy = y + 0.5f;
         boolean topQ = cy < halfH;
         for (int x = 0; x < width; ++x) {
            float cx = x + 0.5f;
            boolean leftQ = cx < halfW;
            float cr = topQ ? (leftQ ? rtl : rtr) : (leftQ ? rbl : rbr);
            float coverage;
            if (cr <= 0.0f) {
               coverage = 1.0f;
            } else {
               float bx = leftQ ? cr : width - cr;
               float by = topQ ? cr : height - cr;
               float dx = leftQ ? bx - cx : cx - bx;
               float dy = topQ ? by - cy : cy - by;
               if (dx < 0.0f) {
                  dx = 0.0f;
               }
               if (dy < 0.0f) {
                  dy = 0.0f;
               }
               float distance = (float)Math.sqrt(dx * dx + dy * dy) - cr;
               coverage = 0.5f - distance * 2.0f;
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

   private static byte[] ensureB(byte[] array, int size) {
      return array.length >= size ? array : new byte[size];
   }

   private static float[] ensureF(float[] array, int size) {
      return array.length >= size ? array : new float[size];
   }

   private FentRefraction() {
   }
}
