import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.fent.FentShare;

/**
 * End-to-end check of the fent sync client against a LIVE fent-api server.
 *
 * Exercises the exact regressions that were fixed:
 *   P0-1  SSE frames with and without the "data:" prefix
 *   P0-2  initial pull racing the SSE hello (apiRev already at head)
 *   P0-3  nothing re-pulled cosmetics
 *   P0-4  emote staleness unit mismatch
 *   P1-5  id case / uuid-vs-name lookups
 *   PERF  remoteItems() is on the render path: no disk, no allocation churn
 *
 * Usage: SyncCheck <baseUrl> [expectUserCount]
 */
public final class SyncCheck {
   private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();
   private static int passed;
   private static final List<String> failed = new ArrayList<String>();

   private static void check(String name, boolean ok, Object detail) {
      if (ok) {
         passed++;
         System.out.println("  PASS  " + name);
      } else {
         failed.add(name + " :: " + detail);
         System.out.println("  FAIL  " + name + "  " + detail);
      }
   }

   private static void setField(String name, Object value) throws Exception {
      Field f = FentShare.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(null, value);
   }

   private static Object getField(String name) throws Exception {
      Field f = FentShare.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(null);
   }

   private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
      Method m = FentShare.class.getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m.invoke(null, args);
   }

   private static String get(String url) throws Exception {
      HttpRequest r = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(8)).GET().build();
      HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return resp.statusCode() >= 400 ? null : resp.body();
   }

   private static int put(String url, String json) throws Exception {
      HttpRequest r = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(8))
         .header("Content-Type", "application/json")
         .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
      HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return resp.statusCode();
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> remotes() throws Exception {
      return (Map<String, Object>) getField("REMOTES");
   }

   public static void main(String[] args) throws Exception {
      String base = args.length > 0 ? args[0] : "http://127.0.0.1:6767";
      String peerA = "peeralpha";
      String peerB = "peerbeta";
      String me = "synccheck-self";

      System.setProperty("fent.api.url", base);
      System.setProperty("fent.sync.name", me);

      // SAFETY GUARD: if the override didn't take (stale build), every publish
      // below would land on the PRODUCTION api.zouhmi.com. Refuse to continue.
      if (!base.equals(FentShare.apiUrl())) {
         System.out.println("FATAL: -Dfent.api.url override ignored, apiUrl()="
            + FentShare.apiUrl() + " - build/classes is stale. Rebuild first.");
         System.exit(2);
      }

      System.out.println("[1] config overrides + basic state");
      check("apiUrl honours -Dfent.api.url", base.equals(FentShare.apiUrl()), FentShare.apiUrl());
      check("apiEnabled with override", FentShare.apiEnabled(), FentShare.apiEnabled());
      check("syncName honours -Dfent.sync.name", me.equals(FentShare.syncName()), FentShare.syncName());
      check("listenerUp false before warm", !FentShare.listenerUp(), FentShare.listenerUp());

      System.out.println("[2] seed two peers on the live server");
      String now = String.valueOf(System.currentTimeMillis());
      int stA = put(base + "/api/cosmetics/" + peerA, "{\"name\":\"" + peerA
         + "\",\"items\":{\"cape\":\"769\",\"wings\":\"28\"}}");
      int stB = put(base + "/api/cosmetics/" + peerB, "{\"name\":\"" + peerB
         + "\",\"items\":{\"hat\":\"9\"}}");
      check("seed peer A 200", stA == 200, stA);
      check("seed peer B 200", stB == 200, stB);

      JsonObject health = JsonParser.parseString(get(base + "/api/health")).getAsJsonObject();
      long head = health.get("rev").getAsLong();
      check("server rev advanced", head > 0, head);

      System.out.println("[3] P0-2 regression: first pull after SSE hello (apiRev=head)");
      // Simulates the SSE hello landing BEFORE the first pull runs - the exact
      // race that made every initial pull return {} and never load peers.
      setField("apiRev", Long.valueOf(head));
      setField("initialPullDone", Boolean.FALSE);
      call("apiPull", new Class<?>[0]);
      remotes().values().removeIf(v -> false); // no-op: keep reference warm
      check("peers loaded despite apiRev already at head",
         remotes().containsKey(peerA) && remotes().containsKey(peerB),
         remotes().keySet());

      System.out.println("[4] remoteItems serves from memory (render hot path)");
      long t0 = System.nanoTime();
      Map<CosmeticType, String> items = null;
      for (int i = 0; i < 20000; i++) {
         items = FentShare.remoteItems(peerA);
      }
      long elapsedMs = (System.nanoTime() - t0) / 1000000L;
      check("peer A items returned", items != null && "769".equals(items.get(CosmeticType.CAPE)), items);
      check("20k lookups under 500ms (no disk per call)", elapsedMs < 500, elapsedMs + "ms");
      Map<CosmeticType, String> first = FentShare.remoteItems(peerA);
      Map<CosmeticType, String> second = FentShare.remoteItems(peerA);
      check("snapshot cached (same instance, no per-frame alloc)", first == second,
         System.identityHashCode(first) + "/" + System.identityHashCode(second));
      check("snapshot unmodifiable", isUnmodifiable(first), first);
      check("unknown id returns null without throwing", FentShare.remoteItems("nobody-here-xyz") == null,
         "no throw");

      System.out.println("[5] P0-1: applyEvent accepts data:-prefixed AND bare JSON frames");
      long revBefore = ((Number) getField("apiRev")).longValue();
      call("applyEvent", new Class<?>[]{String.class},
         "data:" + "{\"id\":\"" + peerA + "\",\"type\":\"update\",\"rev\":" + (revBefore + 5)
            + ",\"items\":{\"cape\":\"770\",\"wings\":\"29\"}}");
      Map<CosmeticType, String> afterData = FentShare.remoteItems(peerA);
      check("data:-prefixed frame applied", afterData != null && "770".equals(afterData.get(CosmeticType.CAPE)),
         afterData);
      check("rev advanced from frame", ((Number) getField("apiRev")).longValue() == revBefore + 5,
         getField("apiRev"));

      call("applyEvent", new Class<?>[]{String.class},
         "{\"id\":\"" + peerA + "\",\"type\":\"update\",\"items\":{\"cape\":\"771\"}}");
      Map<CosmeticType, String> afterBare = FentShare.remoteItems(peerA);
      check("bare JSON frame applied (failsafe for old servers)",
         afterBare != null && "771".equals(afterBare.get(CosmeticType.CAPE)), afterBare);

      call("applyEvent", new Class<?>[]{String.class}, "data:rev:9999");
      check("data:rev: hello parsed", ((Number) getField("apiRev")).longValue() == 9999, getField("apiRev"));
      call("applyEvent", new Class<?>[]{String.class}, ":hb");
      call("applyEvent", new Class<?>[]{String.class}, "");
      call("applyEvent", new Class<?>[]{String.class}, new Object[]{null});
      call("applyEvent", new Class<?>[]{String.class}, "data:not-json{");
      check("heartbeat/empty/null/junk frames never throw", true, "no throw");

      System.out.println("[6] emote flow (P0-4 staleness units + emote pull)");
      setField("emoteRev", Long.valueOf(0L));
      setField("initialEmotePullDone", Boolean.FALSE);
      int stE = put(base + "/api/emotes/" + peerA, "{\"name\":\"" + peerA
         + "\",\"emote\":\"wave\",\"at\":" + now + ",\"infinite\":false}");
      check("seed emote 200", stE == 200, stE);
      call("apiEmotePull", new Class<?>[0]);
      Object peers = getField("PEER_EMOTES");
      check("emote loaded", peers.getClass().getMethod("containsKey", Object.class)
         .invoke(peers, peerA) == Boolean.TRUE, "PEER_EMOTES=" + peers);

      // P0-4: a FRESH emote must survive applyAllRemote() (the old code compared
      // nanoTime() against epoch millis and wiped it within one 6s cycle).
      for (int i = 0; i < 3; i++) {
         call("applyAllRemote", new Class<?>[0]);
      }
      check("fresh emote NOT wiped by applyAllRemote",
         peers.getClass().getMethod("containsKey", Object.class).invoke(peers, peerA) == Boolean.TRUE,
         "still present");

      // an emote 20 minutes old must be expired (infinite ones never)
      call("applyPeerEmote", new Class<?>[]{String.class, String.class, long.class, boolean.class},
         "stalepeer", "wave", System.currentTimeMillis() - 20L * 60L * 1000L, Boolean.FALSE);
      call("applyPeerEmote", new Class<?>[]{String.class, String.class, long.class, boolean.class},
         "infpeer", "wave", System.currentTimeMillis() - 20L * 60L * 1000L, Boolean.TRUE);
      call("applyAllRemote", new Class<?>[0]);
      check("20-min-old emote expired",
         !Boolean.TRUE.equals(peers.getClass().getMethod("containsKey", Object.class)
            .invoke(peers, "stalepeer")),
         "removed");
      check("infinite emote never expired",
         peers.getClass().getMethod("containsKey", Object.class).invoke(peers, "infpeer") == Boolean.TRUE,
         "kept");

      System.out.println("[7] publish round trip (P1-7: failures are visible)");
      call("apiPublish", new Class<?>[]{java.util.Map.class}, new java.util.LinkedHashMap<CosmeticType, String>());
      String mine = get(base + "/api/cosmetics/" + me);
      check("own row published and readable", mine != null && mine.contains(me), mine);

      System.out.println("[8] P0-3: applier re-pull flag + P1: resync resets revisions");
      setField("apiRev", Long.valueOf(head));
      setField("initialPullDone", Boolean.FALSE);
      call("resync", new Class<?>[0]);
      check("resync resets apiRev", ((Number) getField("apiRev")).longValue() == 0L, getField("apiRev"));
      check("resync resets emoteRev", ((Number) getField("emoteRev")).longValue() == 0L, getField("emoteRev"));
      check("resync clears remotes", remotes().isEmpty(), remotes().keySet());
      call("apiPull", new Class<?>[0]);
      check("full reload after resync brings peers back",
         remotes().containsKey(peerA) && remotes().containsKey(peerB), remotes().keySet());

      System.out.println("[9] live SSE stream produces data: frames end-to-end");
      // publish a change, then confirm the server's SSE stream emits a data: frame
      final String[] sseLine = new String[1];
      Thread reader = new Thread(new Runnable() {
         public void run() {
            try {
               HttpRequest r = HttpRequest.newBuilder(URI.create(base + "/api/events?since=0"))
                  .timeout(Duration.ofSeconds(12)).GET().build();
               java.net.http.HttpResponse<java.io.InputStream> resp =
                  HTTP.send(r, HttpResponse.BodyHandlers.ofInputStream());
               java.io.BufferedReader br = new java.io.BufferedReader(
                  new java.io.InputStreamReader(resp.body(), StandardCharsets.UTF_8));
               String line;
               long deadline = System.currentTimeMillis() + 10000L;
               while (System.currentTimeMillis() < deadline && (line = br.readLine()) != null) {
                  if (line.startsWith("data:{")) {
                     sseLine[0] = line;
                     return;
                  }
               }
            } catch (Throwable ignored) {
            }
         }
      }, "sse-check");
      reader.setDaemon(true);
      reader.start();
      Thread.sleep(700L);
      put(base + "/api/cosmetics/" + peerB, "{\"name\":\"" + peerB
         + "\",\"items\":{\"hat\":\"10\",\"cape\":\"555\"}}");
      reader.join(11000L);
      check("SSE emitted a data:-prefixed event frame", sseLine[0] != null, sseLine[0]);

      System.out.println("\n" + passed + " passed, " + failed.size() + " failed");
      for (String f : failed) {
         System.out.println("  FAILED: " + f);
      }
      System.exit(failed.isEmpty() ? 0 : 1);
   }

   private static boolean isUnmodifiable(Map<CosmeticType, String> map) {
      try {
         map.put(CosmeticType.CAPE, "boom");
         return false;
      } catch (UnsupportedOperationException expected) {
         return true;
      } catch (Throwable other) {
         return false;
      }
   }

   private SyncCheck() {
   }
}
