package net.silentclient.fent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import net.silentclient.common.api.bridge.MinecraftBridge;
import net.silentclient.common.api.setting.ModeSetting;
import net.silentclient.common.api.setting.NumberSetting;

public final class FentWidget {
   private static final String OFFSET_NAME = "Lyrics Offset";
   private static final float SLOT_SIZE = 9.0f;
   private static final long DAY_TICKS = 24000L;
   private static final String[] MODES = new String[] {
      "None", "FPS", "Ping", "Time", "Date", "Day", "Biome", "Players", "Version", "Server IP",
      "Track", "Artist", "Song Time", "Current Lyric"
   };

   private static NumberSetting offset;
   private static boolean offsetBroken;
   private static Method colorOf;
   private static Method drawSlot;
   private static SimpleDateFormat dateFormat;
   private static String dateText = "";
   private static long dateMinute = Long.MIN_VALUE;
   private static boolean broken;
   private static boolean drawWarned;

   @SuppressWarnings("unchecked")
   public static synchronized void registerOffset(Object widget) {
      if (widget == null || offsetBroken) {
         return;
      }
      if (offset != null) {
         shareOffset(widget);
         return;
      }
      try {
         Method settings = widget.getClass().getMethod("settings");
         Object list = settings.invoke(widget);
         if (!(list instanceof List)) {
            offsetBroken = true;
            System.err.println("[Fent] the Dynamic Widget has no settings list to add to");
            return;
         }
         List<Object> values = (List<Object>)list;
         NumberSetting setting = new NumberSetting(OFFSET_NAME, 0.0, -2.0, 2.0, 0.1).withUnit("s");
         if (!values.contains(setting)) {
            try {
               values.add(setting);
            } catch (RuntimeException unsupported) {
               offsetBroken = true;
               System.err.println("[Fent] the Dynamic Widget settings list is fixed: " + unsupported);
               return;
            }
         }
         offset = setting;
      } catch (Throwable throwable) {
         offsetBroken = true;
         System.err.println("[Fent] could not add the lyrics offset setting: " + throwable);
      }
   }

   @SuppressWarnings("unchecked")
   private static void shareOffset(Object widget) {
      try {
         Object list = widget.getClass().getMethod("settings").invoke(widget);
         if (list instanceof List) {
            List<Object> values = (List<Object>)list;
            if (!values.contains(offset)) {
               values.add(offset);
            }
         }
      } catch (Throwable ignored) {
      }
   }

   public static synchronized void expandModes(Object... slots) {
      for (Object slot : slots) {
         if (!(slot instanceof ModeSetting)) {
            continue;
         }
         ModeSetting setting = (ModeSetting)slot;
         try {
            List<String> modes = setting.getModes();
            if (modes.contains("Current Lyric") && modes.contains("Date")) {
               continue;
            }
         } catch (Throwable ignored) {
         }
         try {
            Field backing = ModeSetting.class.getDeclaredField("a");
            backing.setAccessible(true);
            Object current = setting.getValue();
            backing.set(setting, new ArrayList<String>(Arrays.asList(MODES)));
            if (current instanceof String) {
               setting.setValue((String)current);
            }
         } catch (Throwable throwable) {
            System.err.println("[Fent] could not extend a widget slot list: " + throwable);
         }
      }
   }

   public static double offset() {
      NumberSetting setting = offset;
      if (setting == null) {
         return 0.0;
      }
      try {
         Object value = setting.getValue();
         return value instanceof Number ? ((Number)value).doubleValue() : 0.0;
      } catch (Throwable throwable) {
         return 0.0;
      }
   }

   public static void draw(Object widget, String mode, MinecraftBridge minecraftBridge,
                           net.silentclient.common.api.bridge.RenderBridge renderBridge, String font) {
      if (widget == null || mode == null || renderBridge == null || minecraftBridge == null) {
         return;
      }
      String text = text(mode, minecraftBridge);
      if (text == null || text.isEmpty()) {
         return;
      }
      if (!resolve(widget)) {
         return;
      }
      try {
         int color = ((Number)colorOf.invoke(widget)).intValue();
         drawSlot.invoke(widget, text, Integer.valueOf(color), font,
            Float.valueOf(renderBridge.textFontWidth(text, SLOT_SIZE, font)));
      } catch (Throwable throwable) {
         if (!drawWarned) {
            drawWarned = true;
            System.err.println("[Fent] could not draw a widget slot: " + throwable);
         }
      }
   }

   private static boolean resolve(Object widget) {
      if (broken) {
         return false;
      }
      if (colorOf != null && drawSlot != null) {
         return true;
      }
      try {
         Class<?> type = widget.getClass();
         colorOf = type.getDeclaredMethod("S");
         colorOf.setAccessible(true);
         drawSlot = type.getDeclaredMethod("a", String.class, int.class, String.class, float.class);
         drawSlot.setAccessible(true);
         return true;
      } catch (Throwable throwable) {
         broken = true;
         System.err.println("[Fent] the Dynamic Widget slots moved: " + throwable);
         return false;
      }
   }

   private static String text(String mode, MinecraftBridge minecraftBridge) {
      switch (mode) {
         case "Date":
            return date();
         case "Day":
            return minecraftBridge.isInWorld() ? "Day " + (minecraftBridge.getWorldTime() / DAY_TICKS + 1L) : null;
         case "Biome": {
            String biome = minecraftBridge.getBiomeName();
            return biome == null || biome.isEmpty() ? null : biome;
         }
         case "Players": {
            if (!minecraftBridge.isInWorld()) {
               return null;
            }
            int count = minecraftBridge.getOnlinePlayerCount();
            return count + (count == 1 ? " player" : " players");
         }
         case "Track":
            return track();
         case "Artist":
            return artist();
         case "Song Time":
            return songTime();
         case "Current Lyric":
            return lyricLine();
         default:
            return null;
      }
   }

   private static final java.util.Set<String> BROKEN_METHODS =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
   private static volatile Class<?> media;
   private static final java.util.Map<String, Method> MEDIA_METHODS =
      new java.util.concurrent.ConcurrentHashMap<String, Method>();
   private static volatile Class<?> lyricsClass;
   private static volatile Method lyricAt;
   private static volatile boolean lyricsBroken;

   private static volatile long snapFrame = Long.MIN_VALUE;
   private static volatile long snapTickNanos;
   private static volatile Boolean snapPlaying;
   private static volatile String snapTitle;
   private static volatile String snapArtist;
   private static volatile float snapPos = -1.0f;
   private static volatile float snapDur = -1.0f;
   private static volatile boolean snapHavePos;

   private static Object call(String method) {
      if (BROKEN_METHODS.contains(method)) {
         return null;
      }
      try {
         Class<?> type = media;
         if (type == null) {
            type = Class.forName("net.silentclient.internal.az.q");
            media = type;
         }
         Method accessor = MEDIA_METHODS.get(method);
         if (accessor == null) {
            accessor = type.getMethod(method);
            MEDIA_METHODS.put(method, accessor);
         }
         return accessor.invoke(null);
      } catch (Throwable throwable) {
         BROKEN_METHODS.add(method);
         System.err.println("[Fent] a media slot source moved: " + throwable);
         return null;
      }
   }

   private static void snapshot() {
      long now = System.nanoTime();
      if (snapFrame != Long.MIN_VALUE && now - snapTickNanos < 100000000L && now >= snapTickNanos) {
         return;
      }
      snapTickNanos = now;
      snapFrame = now;
      Object playing = call("f");
      snapPlaying = playing instanceof Boolean ? (Boolean) playing : null;
      if (Boolean.TRUE.equals(snapPlaying)) {
         Object title = call("b");
         snapTitle = title instanceof String ? (String) title : null;
         Object artist = call("a");
         snapArtist = artist instanceof String ? (String) artist : null;
      }
      Object position = call("i");
      Object duration = call("h");
      if (position instanceof Number && duration instanceof Number) {
         snapPos = ((Number) position).floatValue();
         snapDur = ((Number) duration).floatValue();
         snapHavePos = true;
      } else {
         snapHavePos = false;
      }
   }

   private static String track() {
      try {
         snapshot();
         if (!Boolean.TRUE.equals(snapPlaying)) {
            return null;
         }
         String title = snapTitle;
         return title == null || title.isEmpty() ? null : title;
      } catch (Throwable throwable) {
         return null;
      }
   }

   private static String artist() {
      try {
         snapshot();
         if (!Boolean.TRUE.equals(snapPlaying)) {
            return null;
         }
         String artist = snapArtist;
         return artist == null || artist.isEmpty() ? null : artist;
      } catch (Throwable throwable) {
         return null;
      }
   }

   private static String songTime() {
      try {
         snapshot();
         if (!snapHavePos) {
            return null;
         }
         float at = snapPos;
         float of = snapDur;
         if (at < 0.0f || of < 1.0f) {
            return null;
         }
         if (at > of) {
            at = of;
         }
         return clock(at) + " / " + clock(of);
      } catch (Throwable throwable) {
         return null;
      }
   }

   private static String clock(float seconds) {
      int total = Math.max(0, (int)seconds);
      return total / 60 + ":" + String.format(Locale.ROOT, "%02d", total % 60);
   }

   private static String lyricLine() {
      try {
         snapshot();
         if (!snapHavePos) {
            return null;
         }
         float when = (float)(snapPos + offset());
         if (!Boolean.TRUE.equals(snapPlaying)) {
            return null;
         }
         if (!lyricsBroken && (lyricsClass == null || lyricAt == null)) {
            try {
               Class<?> type = Class.forName("net.silentclient.internal.az.l");
               lyricsClass = type;
               lyricAt = type.getMethod("a", float.class);
            } catch (Throwable throwable) {
               lyricsBroken = true;
               System.err.println("[Fent] the lyric line source moved: " + throwable);
               return null;
            }
         }
         if (lyricsBroken || lyricAt == null) {
            return null;
         }
          Object line = lyricAt.invoke(null, Float.valueOf(when));
          String text = line instanceof String ? (String)line : null;
          return text == null || text.isEmpty() ? null : text;
      } catch (Throwable throwable) {
         return null;
      }
   }

   private static synchronized String date() {
      long minute = System.currentTimeMillis() / 60000L;
      if (minute != dateMinute) {
         if (dateFormat == null) {
            dateFormat = new SimpleDateFormat("d MMM", Locale.getDefault());
         }
         dateFormat.setTimeZone(TimeZone.getDefault());
         dateText = dateFormat.format(new Date());
         dateMinute = minute;
      }
      return dateText;
   }

   private FentWidget() {
   }
}
