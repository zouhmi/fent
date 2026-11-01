package net.silentclient.fent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.silentclient.common.api.services.cosmetics.CosmeticType;

public final class FentShare {
   private static final String PREFIX = "FENT1-";
   private static final long REMOTE_TTL_NANOS = 30L * 60L * 1000000000L;
   private static final int MAX_ID_LENGTH = 64;

   private static final Gson GSON = new Gson();
   private static final ConcurrentHashMap<String, Remote> REMOTES = new ConcurrentHashMap<String, Remote>();
   private static volatile Path dir;
   private static volatile long lastPublishNanos;

   private static final class Remote {
      final Map<CosmeticType, String> items;
      final long atNanos;
      Remote(Map<CosmeticType, String> items, long atNanos) {
         this.items = items;
         this.atNanos = atNanos;
      }
   }

   public static Map<CosmeticType, String> snapshot() {
      Map<CosmeticType, String> out = new EnumMap<CosmeticType, String>(CosmeticType.class);
      if (!enabled()) {
         return out;
      }
      for (CosmeticType type : CosmeticType.values()) {
         if (type == null || !FentUnlocks.type(type)) {
            continue;
         }
         String saved;
         try {
            saved = FentUnlocks.string("fent.selected." + type.name().toLowerCase(Locale.ROOT));
         } catch (Throwable ignored) {
            continue;
         }
         if (saved == null || saved.isEmpty() || !idOk(saved)) {
            continue;
         }
         out.put(type, saved);
      }
      return out;
   }

   public static String shareCode() {
      return shareCode(null);
   }

   public static String shareCode(String name) {
      try {
         Map<CosmeticType, String> items = snapshot();
         JsonObject root = new JsonObject();
         root.addProperty("v", 1);
         if (name != null && !name.isEmpty()) {
            root.addProperty("name", name);
         }
         JsonObject body = new JsonObject();
         for (Map.Entry<CosmeticType, String> entry : items.entrySet()) {
            body.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
         }
         root.add("items", body);
         String json = GSON.toJson(root);
         return PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
      } catch (Throwable throwable) {
         return "";
      }
   }

   public static boolean applyShareCode(String code, String playerId) {
      if (code == null || playerId == null || playerId.isEmpty()) {
         return false;
      }
      String text = code.trim();
      if (text.startsWith(PREFIX)) {
         text = text.substring(PREFIX.length());
      }
      if (text.isEmpty() || text.length() > 4096) {
         return false;
      }
      try {
         String json = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
         JsonObject root = GSON.fromJson(json, JsonObject.class);
         if (root == null || !root.has("items") || !root.get("items").isJsonObject()) {
            return false;
         }
         JsonObject body = root.getAsJsonObject("items");
         Map<CosmeticType, String> items = new EnumMap<CosmeticType, String>(CosmeticType.class);
         for (CosmeticType type : CosmeticType.values()) {
            String key = type.name().toLowerCase(Locale.ROOT);
            if (!body.has(key) || body.get(key).isJsonNull()) {
               continue;
            }
            String id = body.get(key).getAsString();
            if (id == null || id.isEmpty() || !idOk(id)) {
               continue;
            }
            items.put(type, id);
         }
         if (items.isEmpty()) {
            return false;
         }
         putRemote(playerId, items);
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   public static Map<CosmeticType, String> remoteItems(String playerId) {
      if (playerId == null || playerId.isEmpty() || !enabled()) {
         return null;
      }
      prune();
      String key = playerId.toLowerCase(Locale.ROOT);
      Remote direct = REMOTES.get(key);
      if (direct == null) {
         Map<CosmeticType, String> fromFile = loadPeerFile(playerId);
         if (fromFile == null || fromFile.isEmpty()) {
            putRemote(key, Collections.<CosmeticType, String>emptyMap());
            return null;
         }
         putRemote(key, fromFile);
         return new EnumMap<CosmeticType, String>(fromFile);
      }
      return direct.items.isEmpty() ? null : new EnumMap<CosmeticType, String>(direct.items);
   }

   public static void publishLocal() {
      if (!enabled()) {
         return;
      }
      long now = System.nanoTime();
      if (now - lastPublishNanos < 1000000000L && now >= lastPublishNanos) {
         return;
      }
      lastPublishNanos = now;
      try {
         Map<CosmeticType, String> items = snapshot();
         Path folder = dir();
         Files.createDirectories(folder);
         String me = localId();
         Path file = folder.resolve(me + ".json");
         JsonObject root = new JsonObject();
         root.addProperty("v", 1);
         root.addProperty("name", me);
         root.addProperty("at", System.currentTimeMillis());
         JsonObject body = new JsonObject();
         for (Map.Entry<CosmeticType, String> entry : items.entrySet()) {
            body.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
         }
         root.add("items", body);
         Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
         Files.write(tmp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
         try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable ignored) {
      }
   }

   private static void putRemote(String playerId, Map<CosmeticType, String> items) {
      REMOTES.put(playerId.toLowerCase(Locale.ROOT), new Remote(new EnumMap<CosmeticType, String>(items), System.nanoTime()));
      if (REMOTES.size() > 128) {
         prune();
      }
   }

   private static void prune() {
      long now = System.nanoTime();
      for (Map.Entry<String, Remote> entry : REMOTES.entrySet()) {
         if (now - entry.getValue().atNanos > REMOTE_TTL_NANOS) {
            REMOTES.remove(entry.getKey(), entry.getValue());
         }
      }
   }

   private static boolean enabled() {
      try {
         return FentUnlocks.shareCosmetics();
      } catch (Throwable throwable) {
         return true;
      }
   }

   private static boolean idOk(String id) {
      if (id.length() > MAX_ID_LENGTH) {
         return false;
      }
      for (int i = 0; i < id.length(); ++i) {
         char c = id.charAt(i);
         boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.';
         if (!ok) {
            return false;
         }
      }
      return true;
   }

   private static Path dir() {
      Path resolved = dir;
      if (resolved == null) {
         synchronized (FentShare.class) {
            resolved = dir;
            if (resolved == null) {
               try {
                  resolved = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                     .resolve("silentclient").resolve("fent-share");
               } catch (Throwable throwable) {
                  resolved = Paths.get("silentclient", "fent-share");
               }
               dir = resolved;
            }
         }
      }
      return resolved;
   }

   private static String localId() {
      try {
         String name = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toString();
         return "me";
      } catch (Throwable ignored) {
      }
      return "me";
   }

   private static String sanitize(String text) {
      StringBuilder out = new StringBuilder(Math.min(text.length(), 48));
      for (int i = 0; i < text.length() && out.length() < 48; ++i) {
         char c = text.charAt(i);
         boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
            || (c >= '0' && c <= '9') || c == '_' || c == '-';
         out.append(ok ? c : '_');
      }
      return out.length() == 0 ? "me" : out.toString();
   }

   private static Map<CosmeticType, String> loadPeerFile(String playerId) {
      try {
         Path folder = dir();
         if (!Files.isDirectory(folder)) {
            return null;
         }
         Path exact = folder.resolve(sanitize(playerId) + ".json");
         if (Files.isRegularFile(exact)) {
            Map<CosmeticType, String> items = readPeerFile(exact);
            if (items != null && !items.isEmpty()) {
               return items;
            }
         }
         try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.json")) {
            for (Path path : stream) {
               String name = path.getFileName().toString();
               if (name.length() > 5) {
                  name = name.substring(0, name.length() - 5);
               }
               if (!name.equalsIgnoreCase(playerId) && !name.equalsIgnoreCase(sanitize(playerId))) {
                  continue;
               }
               Map<CosmeticType, String> items = readPeerFile(path);
               if (items != null && !items.isEmpty()) {
                  return items;
               }
            }
         } catch (Throwable ignored) {
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static Map<CosmeticType, String> readPeerFile(Path file) {
      try {
         String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
         JsonObject root = GSON.fromJson(text, JsonObject.class);
         if (root == null || !root.has("items") || !root.get("items").isJsonObject()) {
            return null;
         }
         JsonObject body = root.getAsJsonObject("items");
         Map<CosmeticType, String> items = new EnumMap<CosmeticType, String>(CosmeticType.class);
         for (CosmeticType type : CosmeticType.values()) {
            String key = type.name().toLowerCase(Locale.ROOT);
            if (!body.has(key) || body.get(key).isJsonNull()) {
               continue;
            }
            String id = body.get(key).getAsString();
            if (id == null || id.isEmpty() || !idOk(id)) {
               continue;
            }
            if (!FentUnlocks.type(type)) {
               continue;
            }
            items.put(type, id);
         }
         return items;
      } catch (Throwable ignored) {
         return null;
      }
   }

   private FentShare() {
   }
}
