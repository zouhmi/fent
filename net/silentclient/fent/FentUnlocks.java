package net.silentclient.fent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.silentclient.common.api.services.cosmetics.CosmeticType;

public final class FentUnlocks {
   private static final com.google.gson.Gson GSON = new Gson();
   private static final long MTIME_TTL_NANOS = 300000000L;

   private static Path configPath() {
      return FabricLoader.getInstance().getGameDir().resolve("silentclient").resolve("config.json");
   }

   private static volatile JsonObject cached;
   private static volatile Path cachedPath;
   private static volatile long cachedModified;
   private static volatile long lastProbeNanos;
   /** Key lookups memoized for the current probe window (menus query these
    *  per row per frame; the old code walked the Gson tree every time). */
   private static final java.util.concurrent.ConcurrentHashMap<String, String> VALUE_MEMO =
      new java.util.concurrent.ConcurrentHashMap<String, String>();
   private static final Object CONFIG_LOCK = new Object();

   /** True while this probe window is fresh enough to serve without I/O. */
   private static boolean windowFresh() {
      long now = System.nanoTime();
      return cached != null && now >= lastProbeNanos && now - lastProbeNanos < MTIME_TTL_NANOS;
   }

   /**
    * Hot path is lock-free: toggle checks run per UI row per frame and used to
    * serialize on the class monitor plus walk the config tree each call. Only
    * one file stat happens per MTIME_TTL window, not per call.
    */
   private static JsonObject readConfig() {
      if (windowFresh()) {
         return cached;
      }
      synchronized (CONFIG_LOCK) {
         if (windowFresh()) { // another thread probed while we waited
            return cached;
         }
         Path path;
         try {
            path = configPath();
         } catch (Throwable noLoader) {
            // FabricLoader unavailable (headless tools/tests): cache an empty
            // config instead of re-throwing on every single toggle lookup.
            store(new JsonObject(), null, 0L);
            return cached;
         }
         try {
            lastProbeNanos = System.nanoTime();
            if (!Files.exists(path)) {
               store(new JsonObject(), path, 0L);
               return cached;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (cached != null && path.equals(cachedPath) && modified == cachedModified) {
               lastProbeNanos = System.nanoTime();
               return cached;
            }
            String text = Files.readString(path);
            JsonObject object = GSON.fromJson(text, JsonObject.class);
            store(object == null ? new JsonObject() : object, path, modified);
            return cached;
         } catch (Throwable throwable) {
            if (cached == null) {
               store(new JsonObject(), path, 0L);
            }
            return cached;
         }
      }
   }

   /** Publishes a snapshot and invalidates memoized lookups. */
   private static void store(JsonObject object, Path path, long modified) {
      cached = object;
      cachedPath = path;
      cachedModified = modified;
      lastProbeNanos = System.nanoTime();
      VALUE_MEMO.clear();
   }

   private static String fileValue(String key) {
      if (windowFresh()) {
         String memo = VALUE_MEMO.get(key);
         if (memo != null) {
            return memo;
         }
      }
      String computed = lookup(key);
      if (windowFresh()) {
         VALUE_MEMO.put(key, computed);
      }
      return computed;
   }

   private static String lookup(String key) {
      try {
         JsonObject root = readConfig();
         if (root.has("settings") && root.get("settings").isJsonObject()) {
            JsonObject settings = root.getAsJsonObject("settings");
            if (settings.has(key) && !settings.get(key).isJsonNull()) {
               return settings.get(key).getAsString();
            }
         }
         if (root.has(key) && !root.get(key).isJsonNull()) {
            return root.get(key).getAsString();
         }
      } catch (Throwable ignored) {
      }
      return "";
   }

   private static String value(String key) {
      return fileValue(key);
   }

   private static boolean on(String key) {
      String value = value("unlocker." + key);
      return value.isEmpty() || Boolean.parseBoolean(value);
   }

   public static boolean onRaw(String key) {
      return on(key);
   }

   public static String string(String key) {
      return value(key);
   }

   public static synchronized void setString(String key, String value) {
      String stored = value == null ? "" : value;
      try {
         Path path = configPath();
         JsonObject root = readConfig();
         JsonObject settings = null;
         if (root.has("settings") && root.get("settings").isJsonObject()) {
            settings = root.getAsJsonObject("settings");
         } else {
            settings = new JsonObject();
            root.add("settings", settings);
         }
         if (stored.isEmpty()) {
            settings.remove(key);
            root.remove(key);
         } else {
            settings.addProperty(key, stored);
         }
         Path parent = path.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }
         Files.writeString(path, GSON.toJson(root));
         store(root, path, Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L);
      } catch (Throwable ignored) {
      }
   }

   public static boolean cosmetics() {
      return on("cosmetics");
   }

   public static boolean emotes() {
      return on("emotes");
   }

   public static boolean themes() {
      return on("themes");
   }

   public static boolean premium() {
      return on("premium");
   }

   public static boolean coinsOff() {
      return on("coins.off");
   }

   private static boolean explicitlyOn(String key) {
      String value = value("unlocker." + key).trim().toLowerCase(Locale.ROOT);
      return "true".equals(value) || "1".equals(value);
   }

   /** Effect-heavy features are opt-in: on some GPUs (notably Intel iGPU) the
     * glass pipeline renders opaque white and eats frames, so never enable it
     * just because the setting is unset. */
   public static boolean liquidGlassUi() {
      return explicitlyOn("aero.glass");
   }

   public static boolean liquidGlassHud() {
      return explicitlyOn("aero.hud");
   }

   public static boolean liquidGlassRefraction() {
      return explicitlyOn("aero.refraction");
   }

   public static boolean lyricsCache() {
      return on("lyrics.cache");
   }

   public static boolean hideColorPicker() {
      return on("colorpicker.off");
   }

   public static boolean type(CosmeticType type) {
      if (type == null) {
         return cosmetics();
      }
      return cosmetics() && on("cosmetics." + type.name().toLowerCase(Locale.ROOT));
   }

   private FentUnlocks() {
   }
}
