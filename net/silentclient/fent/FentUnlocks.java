package net.silentclient.fent;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.silentclient.common.api.services.cosmetics.CosmeticType;

public final class FentUnlocks {
   private static final com.google.gson.Gson GSON = new Gson();
   private static final long MTIME_TTL_NANOS = 300000000L;

   private static Path configPath() {
      return FabricLoader.getInstance().getGameDir().resolve("silentclient").resolve("config.json");
   }

   /** Immutable snapshot: a single volatile reference keeps root/path/mtime/probe atomic. */
   private static final class Snapshot {
      final JsonObject root;
      final Path path;
      final long modified;
      final long probedAt;
      Snapshot(JsonObject root, Path path, long modified, long probedAt) {
         this.root = root;
         this.path = path;
         this.modified = modified;
         this.probedAt = probedAt;
      }
   }

   private static volatile Snapshot SNAP = new Snapshot(null, null, 0L, 0L);
   /** Key lookups memoized for the current probe window (menus query these
    *  per row per frame; the old code walked the Gson tree every time). */
   private static final java.util.concurrent.ConcurrentHashMap<String, String> VALUE_MEMO =
      new java.util.concurrent.ConcurrentHashMap<String, String>();
   private static final Object CONFIG_LOCK = new Object();

   /** True while this probe window is fresh enough to serve without I/O. */
   private static boolean windowFresh() {
      Snapshot snap = SNAP;
      if (snap.root == null) {
         return false;
      }
      long now = System.nanoTime();
      return now >= snap.probedAt && now - snap.probedAt < MTIME_TTL_NANOS;
   }

   /**
    * Hot path is lock-free: toggle checks run per UI row per frame and used to
    * serialize on the class monitor plus walk the config tree each call. Only
    * one file stat happens per MTIME_TTL window, not per call.
    */
   private static JsonObject readConfig() {
      Snapshot snap = SNAP;
      long now = System.nanoTime();
      if (snap.root != null && now >= snap.probedAt && now - snap.probedAt < MTIME_TTL_NANOS) {
         return snap.root;
      }
      synchronized (CONFIG_LOCK) {
         snap = SNAP;
         now = System.nanoTime();
         if (snap.root != null && now >= snap.probedAt && now - snap.probedAt < MTIME_TTL_NANOS) {
            return snap.root; // another thread probed while we waited
         }
         Path path;
         try {
            path = configPath();
         } catch (Throwable noLoader) {
            // FabricLoader unavailable (headless tools/tests): cache an empty
            // config instead of re-throwing on every single toggle lookup.
            store(new JsonObject(), null, 0L);
            return SNAP.root;
         }
         try {
            if (!Files.exists(path)) {
               store(new JsonObject(), path, 0L);
               return SNAP.root;
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (snap.root != null && path.equals(snap.path) && modified == snap.modified) {
               // Content unchanged: keep the tree + memo, just extend the window.
               SNAP = new Snapshot(snap.root, snap.path, snap.modified, System.nanoTime());
               return SNAP.root;
            }
            String text = Files.readString(path);
            JsonObject object = GSON.fromJson(text, JsonObject.class);
            store(object == null ? new JsonObject() : object, path, modified);
            return SNAP.root;
         } catch (Throwable throwable) {
            if (SNAP.root == null) {
               store(new JsonObject(), path, 0L);
            } else {
               // Keep serving the last good tree, but don't spin on a broken file.
               Snapshot cur = SNAP;
               SNAP = new Snapshot(cur.root, cur.path, cur.modified, System.nanoTime());
            }
            return SNAP.root;
         }
      }
   }

   /** Publishes a snapshot and invalidates memoized lookups. */
   private static void store(JsonObject object, Path path, long modified) {
      SNAP = new Snapshot(object, path, modified, System.nanoTime());
      VALUE_MEMO.clear();
   }

   private static String fileValue(String key) {
      String memo = VALUE_MEMO.get(key);
      if (memo != null && windowFresh()) {
         return memo;
      }
      String computed = lookup(key);
      if (windowFresh()) {
         VALUE_MEMO.put(key, computed);
      }
      return computed;
   }

   private static String asString(JsonObject owner, String key) {
      try {
         if (!owner.has(key)) {
            return null;
         }
         JsonElement element = owner.get(key);
         if (element == null || element.isJsonNull()) {
            return null;
         }
         if (!element.isJsonPrimitive()) {
            return null;
         }
         return element.getAsString();
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static String lookup(String key) {
      try {
         JsonObject root = readConfig();
         if (root.has("settings") && root.get("settings").isJsonObject()) {
            String fromSettings = asString(root.getAsJsonObject("settings"), key);
            if (fromSettings != null) {
               return fromSettings;
            }
         }
         String top = asString(root, key);
         if (top != null) {
            return top;
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

   public static void setString(String key, String value) {
      String stored = value == null ? "" : value;
      synchronized (CONFIG_LOCK) {
         try {
            Path path = configPath();
            JsonObject root = readConfig();
            // Equip re-applies call b() per type per join, each funneling here
            // with the value already stored: skip the tmp-write + move + Gson
            // reparse when nothing changed.
            String current = asString(root, key);
            if (current == null && root.has("settings") && root.get("settings").isJsonObject()) {
               current = asString(root.getAsJsonObject("settings"), key);
            }
            if (stored.equals(current == null ? "" : current)) {
               return;
            }
            // Copy-on-write: never mutate the published snapshot in place,
            // readers may be walking it lock-free on the render thread.
            JsonObject copy = root.deepCopy();
            JsonObject settings;
            if (copy.has("settings") && copy.get("settings").isJsonObject()) {
               settings = copy.getAsJsonObject("settings");
            } else {
               settings = new JsonObject();
               copy.add("settings", settings);
            }
            if (stored.isEmpty()) {
               settings.remove(key);
               copy.remove(key);
            } else {
               settings.addProperty(key, stored);
            }
            Path parent = path.getParent();
            if (parent != null) {
               Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(copy));
            try {
               Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE,
                  StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
               Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            store(copy, path, Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L);
         } catch (Throwable ignored) {
         }
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
