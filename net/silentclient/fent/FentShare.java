package net.silentclient.fent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.silentclient.common.api.services.cosmetics.CosmeticType;

/**
 * Cosmetic appearance sharing.
 *
 * Two mechanisms, both keyed by the id each EquippedCosmetics packet carries:
 *
 * 1. Local share codes (FENT1-...) and local peer files, unchanged from 1.8.9.
 * 2. Optional homelab sync API (/fentshare api). Every player publishes their
 *    appearance to the API; every client pulls and also receives realtime
 *    pushes, so fent users see each other's cosmetics without locally owning
 *    them and without trading share codes after every change.
 */
public final class FentShare {
   private static final String PREFIX = "FENT1-";
   private static final String API_URL_KEY = "fent.api.url";
   private static final String API_KEY_KEY = "fent.api.key";
   private static final String API_NAME_KEY = "fent.sync.name";
   private static final String API_GENERATED_KEY = "fent.sync.generated";
   private static final String API_OFF_KEY = "fent.api.off";
   private static final String DEFAULT_API_URL = "https://api.zouhmi.com";
   private static final long REMOTE_TTL_NANOS = 30L * 60L * 1000000000L;
   private static final long NET_CACHE_TTL_NANOS = 3L * 1000000000L;
   private static final int MAX_ID_LENGTH = 64;

   private static final Gson GSON = new Gson();
   private static final ConcurrentHashMap<String, Remote> REMOTES = new ConcurrentHashMap<String, Remote>();
   private static final ConcurrentHashMap<String, Long> NET_FETCHED = new ConcurrentHashMap<String, Long>();
   private static volatile Path dir;
   private static volatile long lastPublishNanos;
   private static volatile long publishQueuedAt = -1L;
   private static volatile long publishGeneration;
   private static volatile ScheduledExecutorService POSTS;
   private static volatile HttpClient HTTP;
   private static volatile long apiRev;
   private static volatile long lastApiPullNanos;
   /** Full re-applies heal SilentClient-overwritten records; doing one every
    *  second (the CosmeticWatch cadence) is pure reflection churn when the
    *  data didn't change, so unchanged pulls only re-apply on this cadence. */
   private static volatile long lastApplyNanos;
   private static final long APPLY_EVERY_NANOS = 6000000000L;
   private static volatile long lastNetErrorAt;
   private static volatile long lastPruneNanos;
   /** NET_FETCHED is append-only otherwise: one Long per distinct name seen.
    *  Cap it so a server with heavy churn can't grow it without bound. */
   private static final int MAX_FETCHED = 1024;

   private static void fent$noteFetched(String key) {
      NET_FETCHED.put(key, Long.valueOf(System.nanoTime()));
      if (NET_FETCHED.size() > MAX_FETCHED) {
         int budget = MAX_FETCHED / 4;
         for (String old : NET_FETCHED.keySet().toArray(new String[0])) {
            if (budget-- <= 0) {
               break;
            }
            NET_FETCHED.remove(old);
         }
      }
   }
   /** First pull after boot must be a full snapshot (see apiPull). */
   private static volatile boolean initialPullDone;
   private static volatile boolean initialEmotePullDone;
   /** Separate throttles: a stop must never be dropped by a start's stamp. */
   private static volatile long lastEmoteStopPublishNanos;
   private static volatile boolean started;
   private static final ConcurrentHashMap<String, PeerEmote> PEER_EMOTES = new ConcurrentHashMap<String, PeerEmote>();
   private static volatile long emoteRev;
   private static volatile long lastEmotePublishNanos;
   private static volatile Thread applierThread;
   /** Shared empty map: a peer whose row exists but holds no items. Never stored,
    * only passed through so viewers clear previously rendered cosmetics. */
   private static final Map<CosmeticType, String> EMPTY_ITEMS =
      Collections.<CosmeticType, String>emptyMap();

   private static final class PeerEmote {
      final String emote;
      final long startedAt;
      final boolean infinite;
      PeerEmote(String emote, long startedAt, boolean infinite) {
         this.emote = emote;
         this.startedAt = startedAt;
         this.infinite = infinite;
      }
   }

   private static final class Remote {
      final Map<CosmeticType, String> items;
      final long atNanos;
      private volatile Map<CosmeticType, String> snapshot;
      Remote(Map<CosmeticType, String> items, long atNanos) {
         this.items = items;
         this.atNanos = atNanos;
      }

      /** Immutable copy built once; the hot path must not allocate per frame. */
      Map<CosmeticType, String> snapshot() {
         Map<CosmeticType, String> have = snapshot;
         if (have == null) {
            have = Collections.unmodifiableMap(new EnumMap<CosmeticType, String>(items));
            snapshot = have;
         }
         return have;
      }
   }

   /** uuid -> display-name resolution, negative-cached (see aliasFor). */
   private static final class Alias {
      final String name;
      final long atNanos;
      Alias(String name, long atNanos) {
         this.name = name;
         this.atNanos = atNanos;
      }
   }

   private static final ConcurrentHashMap<String, Alias> ALIASES =
      new ConcurrentHashMap<String, Alias>();
   private static final long ALIAS_TTL_NANOS = 10L * 1000000000L;

   /**
    * Maps an EquippedCosmetics id ("me" or a raw uuid) onto the display name
    * the sync API keys rows by. Mirrors SilentClient's own registry (F.b is the
    * name index, record field "b" holds the name). Negative results are cached
    * too, otherwise every cosmetic row would reflect per frame.
    */
   private static String aliasFor(String playerId, String key) {
      long now = System.nanoTime();
      Alias have = ALIASES.get(key);
      if (have != null && now >= have.atNanos && now - have.atNanos < ALIAS_TTL_NANOS) {
         return have.name;
      }
      String resolved = null;
      try {
         net.silentclient.internal.aq.F f = net.silentclient.internal.aq.F.a();
         Object record = null;
         try {
            record = f.a(playerId); // uuid-shaped lookup
         } catch (Throwable ignored) {
         }
         if (record == null) {
            try {
               record = f.b(playerId); // already a name?
            } catch (Throwable ignored) {
            }
         }
         if (record == null && "me".equals(key)) {
            String local = null;
            try {
               local = net.silentclient.internal.aq.w.a();
            } catch (Throwable ignored) {
            }
            if (local != null && !local.isEmpty() && !"me".equals(local)) {
               try {
                  record = f.b(local);
               } catch (Throwable ignored) {
               }
            }
         }
         if (record != null) {
            Object name = record.getClass().getField("b").get(record);
            if (name instanceof String && !((String) name).isEmpty()) {
               resolved = ((String) name).toLowerCase(Locale.ROOT);
            }
         }
      } catch (Throwable ignored) {
      }
      if (resolved == null || resolved.equals(key)) {
         resolved = null; // remember the miss: skip reflection until the TTL
      }
      if (ALIASES.size() > 256) {
         ALIASES.clear();
      }
      ALIASES.put(key, new Alias(resolved, now));
      return resolved;
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
            saved = null;
         }
         if (saved == null || saved.isEmpty() || !idOk(saved)) {
            try {
               saved = net.silentclient.internal.aq.p.b(type);
            } catch (Throwable ignored) {
               saved = null;
            }
         }
         if (saved == null || saved.isEmpty() || !idOk(saved)) {
            try {
               saved = net.silentclient.internal.aw.d.e().d(type);
            } catch (Throwable ignored) {
               saved = null;
            }
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
         if (apiEnabled()) {
            final Map<CosmeticType, String> payload = new EnumMap<CosmeticType, String>(items);
            schedulePost(true);
         }
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   /**
    * HOT PATH: called from EquippedCosmetics.getItems() for every cosmetic row
    * every frame. Must never touch disk or the network here - the old code
    * scanned the peer-file directory synchronously, which is what made the
    * cosmetics/modules menus hitch on open. Disk/network work is scheduled
    * onto the sync worker instead, and results are served from memory.
    */
   public static Map<CosmeticType, String> remoteItems(String playerId) {
      if (playerId == null || playerId.isEmpty() || !enabled()) {
         return null;
      }
      pruneThrottled();
      String key = playerId.toLowerCase(Locale.ROOT);
      Remote direct = REMOTES.get(key);
      if (direct != null) {
         return direct.items.isEmpty() ? null : direct.snapshot();
      }
      // EquippedCosmetics carries "me" or a raw uuid, while API rows are keyed
      // by display name - resolve that mismatch or packet merges never match.
      String alias = aliasFor(playerId, key);
      if (alias != null) {
         direct = REMOTES.get(alias);
         if (direct != null) {
            return direct.items.isEmpty() ? null : direct.snapshot();
         }
      }
      // not in memory yet: ask the worker to fetch it (API first, then the
      // peer file) at most once per NET_CACHE_TTL, and return nothing now.
      String fetchKey = alias != null ? alias : key;
      long now = System.nanoTime();
      Long last = NET_FETCHED.get(fetchKey);
      if (last == null || now - last.longValue() >= NET_CACHE_TTL_NANOS) {
         fent$noteFetched(fetchKey);
         scheduleFetch(fetchKey);
      }
      return null;
   }

   /** Prune is O(n); run it at most every 30s instead of per call. */
   private static void pruneThrottled() {
      long now = System.nanoTime();
      if (now - lastPruneNanos < 30000000000L && now >= lastPruneNanos) {
         return;
      }
      lastPruneNanos = now;
      prune();
   }

   public static void publishLocal() {
      if (!enabled()) {
         logNetError("publishLocal skipped: cosmetic sharing is disabled", new RuntimeException());
         return;
      }
      long now = System.nanoTime();
      if (now - lastPublishNanos < 1000000000L && now >= lastPublishNanos) {
         return;
      }
      lastPublishNanos = now;
      try {
         Map<CosmeticType, String> items = snapshot();
         storeLocal(items);
         if (apiEnabled()) {
            schedulePost(false);
         } else {
            logNetError("publishLocal skipped: sync api not enabled", new RuntimeException());
         }
      } catch (Throwable ignored) {
         logNetError("publishLocal failed", ignored);
      }
   }

   /** Called by the emote service mixin when THIS player starts an emote. */
   public static void publishLocalEmote(String uid, String emoteId, long startedAt) {
      if (!fent$uidMatchesLocal(uid)) {
         return;
      }
      publishEmoteNow(emoteId, startedAt, false);
   }

   /** State-aware variant: preserves the wheel infinite/loop flag so viewers
    *  don't expire an emote that is still playing locally. */
   public static void publishLocalEmote(String emoteId, long startedAt, boolean infinite) {
      publishEmoteNow(emoteId, startedAt, infinite);
   }

   /** Fallback service variant (no account id available to check). */
   public static void publishLocalEmote(String emoteId, long startedAt) {
      publishEmoteNow(emoteId, startedAt, false);
   }

   /** Called by the emote service mixin when THIS player stops their emote. */
   public static void publishLocalEmoteStop(String uid) {
      if (!fent$uidMatchesLocal(uid)) {
         return;
      }
      publishEmoteStopNow();
   }

   public static void publishLocalEmoteStop() {
      publishEmoteStopNow();
   }

   private static void publishEmoteNow(String emoteId, long startedAt, boolean infinite) {
      if (!apiEnabled() || emoteId == null || emoteId.isEmpty()) {
         return;
      }
      long now = System.nanoTime();
      if (now - lastEmotePublishNanos < 500000000L && now >= lastEmotePublishNanos) {
         return;
      }
      lastEmotePublishNanos = now;
      lastEmoteStopPublishNanos = 0L; // a fresh start re-arms the stop path
      final String emote = emoteId;
      final long at = startedAt > 0L ? startedAt : System.currentTimeMillis();
      final boolean loop = infinite;
      try {
         startWorker();
         POSTS.submit(new Runnable() {
            public void run() {
               apiPublishEmote(emote, at, loop);
            }
         });
      } catch (Throwable ignored) {
      }
   }

   private static void publishEmoteStopNow() {
      if (!apiEnabled()) {
         return;
      }
      // Stops have their OWN throttle stamp: sharing the start stamp dropped
      // whichever transition came second within the window, leaving viewers
      // with a stuck emote (stop eaten) or a ghost start (start eaten).
      long now = System.nanoTime();
      if (now - lastEmoteStopPublishNanos < 250000000L && now >= lastEmoteStopPublishNanos) {
         return;
      }
      lastEmoteStopPublishNanos = now;
      lastEmotePublishNanos = 0L; // a stop must never eat the next start
      try {
         startWorker();
         POSTS.submit(new Runnable() {
            public void run() {
               apiPublishEmote("", 0L, false);
            }
         });
      } catch (Throwable ignored) {
      }
   }

   /** Reflects the local SilentClient account id; null/failed checks pass. */
   private static boolean fent$uidMatchesLocal(String uid) {
      if (uid == null || uid.isEmpty()) {
         return true;
      }
      try {
         Object value = Class.forName("net.silentclient.internal.aq.I")
            .getDeclaredMethod("d").invoke(null);
         long local = value instanceof Number ? ((Number) value).longValue() : 0L;
         if (local > 0L && !String.valueOf(local).equals(uid.trim())) {
            return false;
         }
      } catch (Throwable ignored) {
      }
      return true;
   }

   /** Startup hook: starts the realtime listener once a sync API is configured. */
   public static void warm() {
      if (started) {
         return;
      }
      started = true;
      try {
         if (apiEnabled()) {
            startWorker();
            POSTS.submit(new Runnable() {
               public void run() {
                  apiPublish(snapshot());
                  apiEmotePull();
                  apiPull();
               }
            });
            netListener();
            fent$ensureApplier();
         }
      } catch (Throwable ignored) {
      }
   }

   public static void resync() {
      try {
         REMOTES.clear();
         NET_FETCHED.clear();
         PEER_EMOTES.clear();
         emoteRev = 0L;
         apiRev = 0L;
         // force full snapshots again, otherwise ?since=<old head> returns {}
         // and a resync silently reloads nothing.
         initialPullDone = false;
         initialEmotePullDone = false;
         lastApiPullNanos = 0L;
         if (apiEnabled()) {
            startWorker();
            POSTS.submit(new Runnable() {
               public void run() {
                  apiEmotePull();
                  apiPull();
                  apiPublish(snapshot());
               }
            });
         }
      } catch (Throwable ignored) {
      }
   }

   public static void setApiUrl(String url) {
      String value = url == null ? "" : url.trim();
      while (value.endsWith("/")) {
         value = value.substring(0, value.length() - 1);
      }
      if (value.isEmpty()) {
         FentUnlocks.setString(API_OFF_KEY, "1");
         REMOTES.clear();
         NET_FETCHED.clear();
         return;
      }
      boolean ok = (value.startsWith("http://") || value.startsWith("https://"))
         && value.length() <= 200 && value.indexOf(32) < 0;
      if (ok) {
         FentUnlocks.setString(API_URL_KEY, value);
         FentUnlocks.setString(API_OFF_KEY, "");
         REMOTES.clear();
         NET_FETCHED.clear();
         warm();
      }
   }

   public static void setApiKey(String key) {
      String value = key == null ? "" : key.trim();
      if (value.length() <= 128 && value.indexOf(32) < 0 && (value.isEmpty() || value.matches("[A-Za-z0-9._\\-]+"))) {
         FentUnlocks.setString(API_KEY_KEY, value);
      }
   }

   public static void setSyncName(String name) {
      String value = name == null ? "" : name.trim();
      if (value.length() <= 48) {
         FentUnlocks.setString(API_NAME_KEY, value);
      }
   }

   public static String apiUrl() {
      try {
         // Debug/failsafe override: -Dfent.api.url=http://host:port (or "off").
         // Bypasses config so diagnostics can repoint the client without a game
         // restart, and lets the headless test harness drive a local server.
         String prop = System.getProperty("fent.api.url");
         if (prop != null && !prop.trim().isEmpty()) {
            String value = prop.trim();
            while (value.endsWith("/")) {
               value = value.substring(0, value.length() - 1);
            }
            if ("off".equalsIgnoreCase(value)) {
               return null;
            }
            if (value.startsWith("http://") || value.startsWith("https://")) {
               return value;
            }
         }
         String off = FentUnlocks.string(API_OFF_KEY);
         if ("1".equals(off)) {
            return null;
         }
         String raw = FentUnlocks.string(API_URL_KEY);
         if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_API_URL;
         }
         String url = raw.trim();
         while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
         }
         if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return DEFAULT_API_URL;
         }
         return url;
      } catch (Throwable throwable) {
         return DEFAULT_API_URL;
      }
   }

   public static boolean defaultUrl() {
      try {
         String off = FentUnlocks.string(API_OFF_KEY);
         if ("1".equals(off)) {
            return true;
         }
         String raw = FentUnlocks.string(API_URL_KEY);
         return raw == null || raw.trim().isEmpty();
      } catch (Throwable ignored) {
         return true;
      }
   }

   public static String apiKey() {
      try {
         String key = FentUnlocks.string(API_KEY_KEY);
         return key == null ? "" : key.trim();
      } catch (Throwable throwable) {
         return "";
      }
   }

   public static String syncName() {
      try {
         String prop = System.getProperty("fent.sync.name");
         if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
         }
      } catch (Throwable ignored) {
      }
      try {
         String name = FentUnlocks.string(API_NAME_KEY);
         if (name != null && !name.isEmpty()) {
            return name;
         }
      } catch (Throwable ignored) {
      }
      try {
         String fromClient = net.silentclient.internal.aq.w.a();
         if (fromClient != null && !fromClient.isEmpty() && !fromClient.equals("me")) {
            return fromClient;
         }
      } catch (Throwable ignored) {
      }
      // Never a shared literal: every nameless client used to publish into the
      // SAME "fent-user" row (last writer wins) and then skipped rendering it
      // as "self", so nobody saw anybody. Persist a stable unique id instead.
      return generatedName();
   }

   /** Stable per-install id, persisted so we keep the same API row next boot. */
   private static String generatedName() {
      try {
         String cached = FentUnlocks.string(API_GENERATED_KEY);
         if (cached != null && !cached.isEmpty() && idOk(cached)) {
            return cached;
         }
      } catch (Throwable ignored) {
      }
      String fresh = "fent-" + Long.toHexString(java.util.concurrent.ThreadLocalRandom.current().nextLong() | 1L);
      try {
         FentUnlocks.setString(API_GENERATED_KEY, fresh);
      } catch (Throwable ignored) {
      }
      return fresh;
   }

   public static boolean apiEnabled() {
      try {
         return apiUrl() != null && enabled();
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static boolean listenerUp() {
      // was: apiEnabled() && !Thread.currentThread().isDaemon() || ...
      // - the !isDaemon() half made this true on ANY non-daemon thread.
      Thread running = netThread;
      return running != null && running.isAlive();
   }

   private static volatile Thread netThread;

   private static synchronized void startWorker() {
      try {
         if (POSTS == null || POSTS.isShutdown()) {
            POSTS = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
               Thread thread = new Thread(runnable, "Fent-Share-Sync");
               thread.setDaemon(true);
               return thread;
            });
         }
      } catch (Throwable ignored) {
      }
   }

   private static HttpClient client() {
      HttpClient have = HTTP;
      if (have == null) {
         synchronized (FentShare.class) {
            have = HTTP;
            if (have == null) {
               try {
                  HTTP = have = HttpClient.newBuilder()
                     .connectTimeout(Duration.ofSeconds(8))
                     .followRedirects(HttpClient.Redirect.NORMAL)
                     .build();
               } catch (Throwable throwable) {
                  return null;
               }
            }
         }
      }
      return have;
   }

   private static HttpRequest.Builder request(String url) {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(10));
      try {
         String key = apiKey();
         if (key != null && !key.isEmpty()) {
            builder.header("Fent-Key", key);
         }
      } catch (Throwable ignored) {
      }
      return builder;
   }

   private static void schedulePost(final boolean immediate) {
      try {
         startWorker();
         final boolean queuedNow;
         synchronized (FentShare.class) {
            long now = System.nanoTime();
            long queuedAt = publishQueuedAt;
            if (queuedAt < 0L || now - queuedAt >= 1000000000L) {
               publishQueuedAt = now;
               queuedNow = true;
            } else {
               queuedNow = false;
            }
         }
         if (queuedNow || immediate) {
            final long wanted = ++publishGeneration;
            POSTS.schedule(new Runnable() {
               public void run() {
                  if (publishGeneration == wanted) {
                     try {
                        apiPublish(snapshot());
                     } catch (Throwable ignored) {
                        logNetError("cosmetic publish failed", ignored);
                     }
                  }
               }
            }, immediate ? 0L : 300L, TimeUnit.MILLISECONDS);
         }
      } catch (Throwable ignored) {
         logNetError("cosmetic publish scheduler failed", ignored);
      }
   }

   private static void scheduleFetch(final String playerId) {
      try {
         startWorker();
         POSTS.submit(new Runnable() {
            public void run() {
               try {
                  apiFetchRemote(playerId);
               } catch (Throwable ignored) {
               }
               // API miss -> fall back to the local peer file, but only on this
               // worker thread (the render path must never scan directories).
               String key = playerId.toLowerCase(Locale.ROOT);
               if (!REMOTES.containsKey(key)) {
                  Map<CosmeticType, String> fromFile = loadPeerFile(playerId);
                  if (fromFile != null && !fromFile.isEmpty()) {
                     putRemote(key, fromFile);
                  }
               }
            }
         });
      } catch (Throwable ignored) {
      }
   }

   private static void apiPublish(Map<CosmeticType, String> items) {
      String base = apiUrl();
      if (base == null || !enabled()) {
         return;
      }
      try {
         String id = syncName();
         if (id == null || id.isEmpty()) {
            return;
         }
         JsonObject root = new JsonObject();
         root.addProperty("v", 2);
         root.addProperty("name", id);
         root.addProperty("at", System.currentTimeMillis());
         JsonObject body = new JsonObject();
         for (Map.Entry<CosmeticType, String> entry : items.entrySet()) {
            body.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
         }
         root.add("items", body);
         HttpRequest request = request(base + "/api/cosmetics/" + sanitize(id))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8))
            .build();
         client().send(request, HttpResponse.BodyHandlers.discarding());
      } catch (Throwable throwable) {
         logNetError("could not reach the cosmetic sync API", throwable);
      }
   }

   private static void apiFetchRemote(String playerId) {
      String base = apiUrl();
      if (base == null || playerId == null) {
         return;
      }
      try {
         HttpRequest request = request(base + "/api/cosmetics/" + sanitize(playerId)).GET().build();
         HttpResponse<String> response =
            client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() >= 400) {
            return;
         }
         JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
         if (root == null) {
            return;
         }
         Map<CosmeticType, String> items = parseItems(root.has("items") && root.get("items").isJsonObject()
            ? root.getAsJsonObject("items") : new JsonObject());
         if (items.isEmpty()) {
            REMOTES.remove(playerId.toLowerCase(Locale.ROOT));
         } else {
            putRemote(playerId, items);
         }
      } catch (Throwable ignored) {
      }
   }

   private static void apiPull() {
      String base = apiUrl();
      if (base == null || !enabled()) {
         return;
      }
      lastApiPullNanos = System.nanoTime();
      boolean changed = false;
      try {
         // The SSE hello advances apiRev to head BEFORE our first pull can
         // run, which made every initial pull return {} (peers published
         // earlier were never loaded). The first pull always fetches ALL.
         boolean first = !initialPullDone;
         String since = !first && apiRev > 0L ? "?since=" + apiRev : "";
         HttpRequest request = request(base + "/api/cosmetics" + since).GET().build();
         HttpResponse<String> response =
            client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() >= 400) {
            return;
         }
         JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
         if (root == null || !root.has("users") || !root.get("users").isJsonObject()) {
            return;
         }
         initialPullDone = true;
         if (root.has("rev") && root.get("rev").getAsNumber() != null) {
            long rev = root.get("rev").getAsLong();
            if (rev > apiRev) {
               apiRev = rev;
            }
         }
         JsonObject users = root.getAsJsonObject("users");
         java.util.List<String> clearedIds = new java.util.ArrayList<String>();
         if (root.has("deleted") && root.get("deleted").isJsonObject()) {
            for (String gone : root.getAsJsonObject("deleted").keySet()) {
               if (REMOTES.remove(gone.toLowerCase(Locale.ROOT)) != null
                     && !clearedIds.contains(gone)) {
                  clearedIds.add(gone);
               }
            }
         }
         for (String id : users.keySet()) {
            if (!users.get(id).isJsonObject()) {
               continue;
            }
            JsonObject user = users.getAsJsonObject(id);
            Map<CosmeticType, String> items = parseItems(user.has("items") && user.get("items").isJsonObject()
               ? user.getAsJsonObject("items") : new JsonObject());
            if (items.isEmpty()) {
               if (REMOTES.remove(id.toLowerCase(Locale.ROOT)) != null) {
                  clearedIds.add(id);
               }
            } else {
               putRemote(id, items);
            }
            fent$noteFetched(id.toLowerCase(Locale.ROOT));
         }
         changed = !users.isEmpty() || !clearedIds.isEmpty();
         for (String clearedId : clearedIds) {
            applyRemoteFor(clearedId, true);
         }
      } catch (Throwable throwable) {
         logNetError("could not refresh shared cosmetics", throwable);
      }
      apiEmotePull();
      long now = System.nanoTime();
      if (changed || now - lastApplyNanos >= APPLY_EVERY_NANOS || now < lastApplyNanos) {
         applyAllRemote();
         lastApplyNanos = now;
      }
   }

   /** Applies cosmetics + current emote of one peer through FentInterop. */
   private static void applyRemoteFor(String playerId) {
      applyRemoteFor(playerId, false);
   }

   /**
    * clear=true means the peer HAD a row and it just became empty/deleted: an
    * explicit empty map is pushed so viewers clear previously rendered
    * cosmetics. A null map (peer never seen) leaves native cosmetics alone.
    */
   private static void applyRemoteFor(String playerId, boolean clear) {
      try {
         if (playerId == null || playerId.isEmpty()) {
            return;
         }
         String key = playerId.toLowerCase(Locale.ROOT);
         Remote remote = REMOTES.get(key);
         PeerEmote playing = PEER_EMOTES.get(key);
         String emote = playing == null ? "" : playing.emote;
         long at = playing == null ? 0L : playing.startedAt;
         boolean infinite = playing != null && playing.infinite;
         Map<CosmeticType, String> items;
         if (remote != null) {
            items = remote.items;
         } else if (clear) {
            items = EMPTY_ITEMS;
         } else {
            items = null;
         }
         FentInterop.applyPlayer(playerId, items, emote, at, infinite);
         if (playing == null) {
            FentInterop.stopEmote(playerId);
         }
      } catch (Throwable ignored) {
      }
   }

   /** Re-pushes every peer into the player registry; also prunes stale emotes. */
   private static void applyAllRemote() {
      try {
         java.util.HashSet<String> names = new java.util.HashSet<String>();
         for (String name : REMOTES.keySet().toArray(new String[0])) {
            names.add(name);
         }
         long now = System.currentTimeMillis();
         for (String key : PEER_EMOTES.keySet().toArray(new String[0])) {
            PeerEmote playing = PEER_EMOTES.get(key);
            // startedAt is epoch MILLIS (server clock) - comparing System.nanoTime()
            // against it wiped every emote within seconds on a long-uptime JVM.
            if (playing != null && !playing.infinite && playing.startedAt > 0L
                  && now - playing.startedAt > 15L * 60L * 1000L
                  && PEER_EMOTES.remove(key) != null) {
               FentInterop.stopEmote(key);
               continue;
            }
            names.add(key);
         }
         for (String name : names.toArray(new String[0])) {
            applyRemoteFor(name);
         }
      } catch (Throwable ignored) {
      }
   }

   private static void apiEmotePull() {
      String base = apiUrl();
      if (base == null || !enabled()) {
         return;
      }
      lastApiPullNanos = System.nanoTime();
      try {
         boolean first = !initialEmotePullDone;
         String since = !first && emoteRev > 0L ? "?since=" + emoteRev : "";
         HttpRequest request = request(base + "/api/emotes" + since).GET().build();
         HttpResponse<String> response =
            client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
         if (response.statusCode() >= 400) {
            return;
         }
         JsonObject root = GSON.fromJson(response.body(), JsonObject.class);
         if (root == null || !root.has("emotes") || !root.get("emotes").isJsonObject()) {
            return;
         }
         initialEmotePullDone = true;
         if (root.has("erev") && root.get("erev").getAsNumber() != null) {
            long erev = root.get("erev").getAsLong();
            if (erev > emoteRev) {
               emoteRev = erev;
            }
         }
         JsonObject emotes = root.getAsJsonObject("emotes");
         if (root.has("deleted") && root.get("deleted").isJsonObject()) {
            for (String gone : root.getAsJsonObject("deleted").keySet()) {
               applyPeerEmote(gone, "", 0L, false);
            }
         }
         for (String id : emotes.keySet()) {
            if (!emotes.get(id).isJsonObject()) {
               continue;
            }
            JsonObject user = emotes.getAsJsonObject(id);
            String emote = user.has("emote") && user.get("emote").isJsonPrimitive()
               ? user.get("emote").getAsString() : "";
            long at = user.has("at") && user.get("at").getAsNumber() != null
               ? user.get("at").getAsLong() : 0L;
            boolean infinite = user.has("infinite") && user.get("infinite").getAsBoolean();
            applyPeerEmote(id, emote, at, infinite);
            applyRemoteFor(id);
         }
      } catch (Throwable throwable) {
         logNetError("could not refresh shared emotes", throwable);
      }
   }

   private static void applyPeerEmote(String id, String emote, long at, boolean infinite) {
      try {
         if (id == null || id.isEmpty()) {
            return;
         }
         String key = id.toLowerCase(Locale.ROOT);
         if (emote == null || emote.isEmpty()) {
            if (PEER_EMOTES.remove(key) != null) {
               FentInterop.stopEmote(id);
            }
            return;
         }
         PeerEmote previous = PEER_EMOTES.get(key);
         boolean same = previous != null && previous.emote.equals(emote)
            && previous.startedAt == at && previous.infinite == infinite;
         if (!same) {
            PEER_EMOTES.put(key, new PeerEmote(emote, at, infinite));
            fent$capPeerEmotes();
         }
      } catch (Throwable ignored) {
      }
   }

   /** Best-effort infinite/loop probe for the local emote state object.
    *  Unknown shapes safely default to false (the old behavior). */
   public static boolean fent$isInfiniteState(Object state) {
      if (state == null) {
         return false;
      }
      String[] names = {"isInfinite", "isLooping", "isLoop", "getInfinite", "getLooping"};
      for (String name : names) {
         Method probe;
         try {
            probe = state.getClass().getMethod(name);
         } catch (NoSuchMethodException missing) {
            continue;
         } catch (Throwable ignored) {
            return false;
         }
         try {
            Class<?> type = probe.getReturnType();
            if (type != boolean.class && type != Boolean.class) {
               continue;
            }
            if (Boolean.TRUE.equals(probe.invoke(state))) {
               return true;
            }
         } catch (Throwable ignored) {
         }
      }
      return false;
   }

   /** PEER_EMOTES only shrinks on the 15-minute expiry; a flood of distinct
    *  emote publishers (or spoofed ids) would otherwise grow it forever. */
   private static void fent$capPeerEmotes() {
      int over = PEER_EMOTES.size() - 256;
      if (over <= 0) {
         return;
      }
      String oldestKey = null;
      long oldestAt = Long.MAX_VALUE;
      for (Map.Entry<String, PeerEmote> entry : PEER_EMOTES.entrySet()) {
         PeerEmote playing = entry.getValue();
         if (playing != null && !playing.infinite && playing.startedAt < oldestAt) {
            oldestAt = playing.startedAt;
            oldestKey = entry.getKey();
         }
      }
      if (oldestKey != null) {
         PEER_EMOTES.remove(oldestKey);
      } else {
         for (String k : PEER_EMOTES.keySet().toArray(new String[0])) {
            if (over-- <= 0) {
               break;
            }
            PEER_EMOTES.remove(k);
         }
      }
   }

   private static void apiPublishEmote(String emoteId, long startedAt, boolean infinite) {
      String base = apiUrl();
      if (base == null || !enabled()) {
         return;
      }
      try {
         String id = syncName();
         if (id == null || id.isEmpty()) {
            return;
         }
         JsonObject root = new JsonObject();
         root.addProperty("v", 1);
         root.addProperty("name", id);
         root.addProperty("emote", emoteId == null ? "" : emoteId);
         root.addProperty("at", startedAt > 0L ? startedAt : System.currentTimeMillis());
         root.addProperty("infinite", infinite);
         HttpRequest request = request(base + "/api/emotes/" + sanitize(id))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(root), StandardCharsets.UTF_8))
            .build();
         client().send(request, HttpResponse.BodyHandlers.discarding());
      } catch (Throwable throwable) {
         logNetError("could not reach the emote sync API", throwable);
      }
   }

   private static void fent$ensureApplier() {
      try {
         if (applierThread != null && applierThread.isAlive()) {
            return;
         }
         Thread thread = new Thread(new Runnable() {
            public void run() {
               long tick = 0L;
               while (true) {
                  try {
                     Thread.sleep(6000L);
                  } catch (InterruptedException ignored) {
                     return;
                  }
                  if (!apiEnabled()) {
                     continue;
                  }
                  ++tick;
                  if (tick % 10L == 0L) {
                     // emotes AND cosmetics: cosmetics were never refreshed
                     // here, so peer changes after boot never arrived.
                     apiEmotePull();
                     apiPull();
                  } else if (tick % 3L == 0L) {
                     apiPull();
                  }
                  applyAllRemote();
               }
            }
         }, "Fent-Share-Applier");
         thread.setDaemon(true);
         applierThread = thread;
         thread.start();
         startCosmeticWatch();
      } catch (Throwable ignored) {
      }
   }

   /**
    * In-game realtime check: the sync api is polled (and applied) every second
    * for cosmetic changes, alongside the SSE push. This guarantees that any
    * cosmetic change made in a server or world is picked up within ~1s even when
    * the SSE stream hiccups or pushes lag behind.
    */
   private static void startCosmeticWatch() {
      try {
         Thread thread = new Thread(new Runnable() {
            public void run() {
               while (true) {
                  try { Thread.sleep(1000L); } catch (InterruptedException ignored) { return; }
                  if (!apiEnabled()) { continue; }
                  apiPull();
               }
            }
         }, "Fent-Share-CosmeticWatch");
         thread.setDaemon(true);
         thread.start();
      } catch (Throwable ignored) {
      }
   }

   /**
    * Realtime tail: long-lived GET /api/events (SSE). Reconnects with backoff;
    * while the stream is healthy the server pushes every change immediately.
    */
   private static void netListener() {
      try {
         if (netThread != null && netThread.isAlive()) {
            return;
         }
         Thread thread = new Thread(new Runnable() {
            public void run() {
               long failures = 0L;
               while (true) {
                  String base = apiUrl();
                  if (base == null) {
                     try {
                        Thread.sleep(5000L);
                     } catch (InterruptedException ignored) {
                        return;
                     }
                     continue;
                  }
                  try {
                     HttpRequest request = request(base + "/api/events?since=" + apiRev)
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                     HttpResponse<InputStream> response = client()
                        .send(request, HttpResponse.BodyHandlers.ofInputStream());
                     if (response.statusCode() >= 400) {
                        failures++;
                     } else {
                        failures = 0; // a clean connection resets the backoff
                        try (BufferedReader reader = new BufferedReader(
                           new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                           String line;
                           while ((line = reader.readLine()) != null) {
                              applyEvent(line);
                           }
                        }
                     }
                  } catch (Throwable ignored) {
                     failures++;
                  }
                  long backoff = Math.min(30000L, 1000L * failures);
                  long sincePull = System.nanoTime() - lastApiPullNanos;
                  if (sincePull > 5000000000L) {
                     apiPull();
                  }
                  try {
                     Thread.sleep(backoff + 2000L);
                  } catch (InterruptedException ignored) {
                     return;
                  }
               }
            }
         }, "Fent-Share-Listener");
         thread.setDaemon(true);
         netThread = thread;
         thread.start();
      } catch (Throwable ignored) {
      }
   }

   private static void applyEvent(String line) {
      try {
         if (line == null) {
            return;
         }
         if (line.startsWith("data:erev:")) {
            long erev = Long.parseLong(line.substring(10).trim());
            if (erev > emoteRev) {
               emoteRev = erev;
            }
            return;
         }
         if (line.startsWith("data:rev:")) {
            long rev = Long.parseLong(line.substring(9).trim());
            if (rev > apiRev) {
               apiRev = rev;
            }
            return;
         }
         String payload;
         if (line.startsWith("data:")) {
            payload = line.substring(5);
         } else if (line.indexOf('{') >= 0) {
            // failsafe: older servers pushed bare JSON without the SSE "data:"
            // prefix - accept it instead of dropping every realtime update.
            payload = line.substring(line.indexOf('{'));
         } else {
            return; // ":hb" comments, empty lines, keep-alives
         }
         JsonObject root = GSON.fromJson(payload, JsonObject.class);
         if (root == null || !root.has("id")) {
            return;
         }
         String id = root.get("id").getAsString();
         if (root.has("type") && root.get("type").isJsonPrimitive()) {
            String type = root.get("type").getAsString();
            if ("emote".equals(type) || "emote-stop".equals(type)) {
               String emote = root.has("emote") && root.get("emote").isJsonPrimitive()
                  ? root.get("emote").getAsString() : "";
               long at = root.has("at") && root.get("at").getAsNumber() != null
                  ? root.get("at").getAsLong() : 0L;
               boolean infinite = root.has("infinite") && root.get("infinite").getAsBoolean();
               applyPeerEmote(id, emote, at, infinite);
               applyRemoteFor(id);
               if (root.has("erev") && root.get("erev").getAsNumber() != null) {
                  long erev = root.get("erev").getAsLong();
                  if (erev > emoteRev) {
                     emoteRev = erev;
                  }
               }
               return;
            }
         }
         Map<CosmeticType, String> items = parseItems(root.has("items") && root.get("items").isJsonObject()
            ? root.getAsJsonObject("items") : new JsonObject());
         // only a row we actually held can transition to cleared; a first-seen
         // empty row leaves the peer's native cosmetics untouched.
         boolean had = REMOTES.remove(id.toLowerCase(Locale.ROOT)) != null;
         boolean cleared = items.isEmpty() && had;
         if (!items.isEmpty()) {
            putRemote(id, items);
         }
         fent$noteFetched(id.toLowerCase(Locale.ROOT));
         applyRemoteFor(id, cleared);
         if (root.has("rev") && root.get("rev").getAsNumber() != null) {
            long rev = root.get("rev").getAsLong();
            if (rev > apiRev) {
               apiRev = rev;
            }
         }
      } catch (Throwable ignored) {
      }
   }

   private static void logNetError(String what, Throwable throwable) {
      long now = System.currentTimeMillis();
      if (now - lastNetErrorAt > 60000L) {
         lastNetErrorAt = now;
         System.err.println("[Fent] " + what + ": " + throwable);
      }
   }

   private static Map<CosmeticType, String> parseItems(JsonObject body) {
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
      return items;
   }

   private static void putRemote(String playerId, Map<CosmeticType, String> items) {
      REMOTES.put(playerId.toLowerCase(Locale.ROOT), new Remote(new EnumMap<CosmeticType, String>(items), System.nanoTime()));
      applyRemoteFor(playerId);
      if (REMOTES.size() > 128) {
         prune();
      }
   }

   private static void prune() {
      long now = System.nanoTime();
      Remote remote;
      for (String key : REMOTES.keySet().toArray(new String[0])) {
         remote = REMOTES.get(key);
         if (remote != null && now - remote.atNanos > REMOTE_TTL_NANOS) {
            REMOTES.remove(key, remote);
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

   private static void storeLocal(Map<CosmeticType, String> items) {
      try {
         Map<CosmeticType, String> safe = new EnumMap<CosmeticType, String>(
            items.isEmpty() ? Collections.<CosmeticType, String>emptyMap() : items);
         Path folder = dir();
         Files.createDirectories(folder);
         Path file = folder.resolve("me.json");
         JsonObject root = new JsonObject();
         root.addProperty("v", 1);
         root.addProperty("name", syncName());
         root.addProperty("at", System.currentTimeMillis());
         JsonObject body = new JsonObject();
         for (Map.Entry<CosmeticType, String> entry : safe.entrySet()) {
            body.addProperty(entry.getKey().name().toLowerCase(Locale.ROOT), entry.getValue());
         }
         root.add("items", body);
         Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
         Files.write(tmp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
         try {
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
         } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (Throwable ignored) {
      }
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
         try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*.json")) {
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
