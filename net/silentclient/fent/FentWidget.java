package net.silentclient.fent;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import net.silentclient.common.api.bridge.MinecraftBridge;
import net.silentclient.common.api.setting.NumberSetting;

// The Dynamic Widget's own private pieces, reached from the mixin: the extra info slots reuse the
// widget's slot drawing, and the lyrics timing control is a setting added to the module's own list
// so the client saves it, loads it and draws it like the ones it ships with.
public final class FentWidget {
   private static final String OFFSET_NAME = "Lyrics Offset";
   private static final float SLOT_SIZE = 9.0f;
   private static final long DAY_TICKS = 24000L;

   private static NumberSetting offset;
   private static Method colorOf;
   private static Method drawSlot;
   private static SimpleDateFormat dateFormat;
   private static String dateText = "";
   private static long dateMinute = Long.MIN_VALUE;
   private static boolean broken;

   // Called from the widget's constructor, which is the point where the client builds the rest of
   // the module's settings.
   public static void registerOffset(Object widget) {
      if (offset != null || broken) {
         return;
      }
      try {
         NumberSetting setting = new NumberSetting(OFFSET_NAME, 0.0, -2.0, 2.0, 0.1).withUnit("s");
         Method settings = widget.getClass().getMethod("settings");
         Object list = settings.invoke(widget);
         if (!(list instanceof List)) {
            broken = true;
            System.err.println("[Fent] the Dynamic Widget has no settings list to add to");
            return;
         }
         ((List<Object>)list).add(setting);
         offset = setting;
      } catch (Throwable throwable) {
         broken = true;
         System.err.println("[Fent] could not add the lyrics offset setting: " + throwable);
      }
   }

   // Seconds the widgets' lyrics are shifted by, negative for earlier.
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

   // Draws one of the extra slots, or nothing when the value has no data to show.
   public static void draw(Object widget, String mode, MinecraftBridge minecraftBridge,
                           net.silentclient.common.api.bridge.RenderBridge renderBridge, String font) {
      if (mode == null || renderBridge == null || minecraftBridge == null) {
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
         broken = true;
         System.err.println("[Fent] could not draw the " + mode + " slot: " + throwable);
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
            int count = minecraftBridge.getOnlinePlayerCount();
            if (count <= 0) {
               return null;
            }
            return count + (count == 1 ? " player" : " players");
         }
         default:
            return null;
      }
   }

   // The date changes once a day, so it is formatted once a minute like the widget's clock.
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
