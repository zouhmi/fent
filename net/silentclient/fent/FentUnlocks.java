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

   private static synchronized JsonObject readConfig() {
      try {
         Path path = configPath();
         long now = System.nanoTime();
         if (cached != null && path.equals(cachedPath) && now - lastProbeNanos < MTIME_TTL_NANOS
            && (now >= lastProbeNanos)) {
            return cached;
         }
         lastProbeNanos = now;
         if (!Files.exists(path)) {
            cached = new JsonObject();
            cachedPath = path;
            cachedModified = 0L;
            return cached;
         }
         long modified = Files.getLastModifiedTime(path).toMillis();
         if (cached != null && path.equals(cachedPath) && modified == cachedModified) {
            return cached;
         }
         String text = Files.readString(path);
         JsonObject object = GSON.fromJson(text, JsonObject.class);
         object = object == null ? new JsonObject() : object;
         cached = object;
         cachedPath = path;
         cachedModified = modified;
         return object;
      } catch (Throwable throwable) {
         JsonObject have = cached;
         return have == null ? new JsonObject() : have;
      }
   }

   private static String fileValue(String key) {
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
      try {
         String live = net.silentclient.internal.aj.t.d(key);
         if (live != null) {
            return live;
         }
      } catch (Throwable ignored) {
      }
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
      boolean throughStore = false;
      try {
         net.silentclient.internal.aj.t.a(key, stored);
         throughStore = true;
      } catch (Throwable ignored) {
      }
      if (throughStore) {
         cached = null;
         lastProbeNanos = 0L;
         return;
      }
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
         cached = root;
         cachedPath = path;
         lastProbeNanos = System.nanoTime();
         if (Files.exists(path)) {
            cachedModified = Files.getLastModifiedTime(path).toMillis();
         }
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

   public static boolean liquidGlassUi() {
      return on("aero.glass");
   }

   public static boolean liquidGlassHud() {
      return on("aero.hud");
   }

   public static boolean liquidGlassRefraction() {
      return on("aero.refraction");
   }

   public static boolean lyricsCache() {
      return on("lyrics.cache");
   }

   public static boolean transparentRows() {
      return on("rows.transparent");
   }

   public static boolean hideColorPicker() {
      return on("colorpicker.off");
   }

   public static boolean shareCosmetics() {
      return on("share.cosmetics");
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
