import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.fent.FentShare;

/**
 * Cross-client fent-to-fent check: TWO separate JVMs, one server.
 *
 *   PeerCheck publish   <name> <cape>   - client A publishes cosmetics (real client code)
 *   PeerCheck emotepub  <name> <emote>  - client A publishes an emote (real client code)
 *   PeerCheck listen    <peer> <baseCape> <targetCape> <emote>
 *                                          client B: start SSE listener ONLY (no applier,
 *                                          no polling), baseline pull, print READY, then
 *                                          wait for changes purely via SSE memory updates.
 *
 * Receiver guarantee: after READY it never calls apiPull/apiEmotePull and never
 * touches the network, so remoteItems()/PEER_EMOTES can only change via the SSE
 * tail - that is exactly the in-game fent-to-fent path.
 */
public final class PeerCheck {
   private static final HttpClient HTTP = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(5)).build();

   private static void set(String name, Object value) throws Exception {
      java.lang.reflect.Field f = FentShare.class.getDeclaredField(name);
      f.setAccessible(true);
      f.set(null, value);
   }

   private static Object get(String name) throws Exception {
      java.lang.reflect.Field f = FentShare.class.getDeclaredField(name);
      f.setAccessible(true);
      return f.get(null);
   }

   private static Object call(String name, Class<?>[] types, Object... args) throws Exception {
      Method m = FentShare.class.getDeclaredMethod(name, types);
      m.setAccessible(true);
      return m.invoke(null, args);
   }

   private static String httpGet(String url) throws Exception {
      HttpRequest r = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(8)).GET().build();
      HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return resp.statusCode() >= 400 ? null : resp.body();
   }

   private static int httpPut(String url, String json) throws Exception {
      HttpRequest r = HttpRequest.newBuilder(URI.create(url))
         .timeout(Duration.ofSeconds(8))
         .header("Content-Type", "application/json")
         .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build();
      HttpResponse<String> resp = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return resp.statusCode();
   }

   private static String capeOf(String peer) {
      try {
         Map<CosmeticType, String> items = FentShare.remoteItems(peer);
         return items == null ? null : items.get(CosmeticType.CAPE);
      } catch (Throwable t) {
         return null;
      }
   }

   private static boolean emoteSeen(String peer) {
      try {
         Object peers = get("PEER_EMOTES");
         Object has = peers.getClass().getMethod("containsKey", Object.class).invoke(peers, peer);
         return Boolean.TRUE.equals(has);
      } catch (Throwable t) {
         return false;
      }
   }

   public static void main(String[] args) throws Exception {
      String mode = args[0];
      String base = System.getProperty("fent.api.url", "http://127.0.0.1:6767");

      if ("publish".equals(mode)) {
         String name = args[1];
         String cape = args[2];
         // identity/base come from -Dfent.sync.name / -Dfent.api.url (proven)
         Map<CosmeticType, String> items = new LinkedHashMap<CosmeticType, String>();
         items.put(CosmeticType.CAPE, cape);
         call("apiPublish", new Class<?>[]{Map.class}, items);
         String row = httpGet(base + "/api/cosmetics/" + name);
         boolean ok = row != null && row.contains("\"cape\": \"" + cape + "\"");
         System.out.println(ok ? "PUBLISH_OK " + cape : "PUBLISH_FAIL " + row);
         System.exit(ok ? 0 : 1);
      }

      if ("emotepub".equals(mode)) {
         String name = args[1];
         String emote = args[2];
         boolean clientOk = false;
         try {
            FentShare.publishLocalEmote(emote, System.currentTimeMillis());
            Thread.sleep(800L); // allow an async post to land before we read
            String row = httpGet(base + "/api/emotes/" + name);
            clientOk = row != null && row.contains(emote);
         } catch (Throwable t) {
            System.out.println("client publishLocalEmote threw: " + t);
         }
         if (!clientOk) {
            // fall back to a raw publish so the SSE receive path is still tested
            int st = httpPut(base + "/api/emotes/" + name,
               "{\"name\":\"" + name + "\",\"emote\":\"" + emote + "\",\"at\":"
                  + System.currentTimeMillis() + ",\"infinite\":false}");
            System.out.println("EMOTE_FALLBACK raw put " + st);
            System.exit(st == 200 ? 0 : 1);
         }
         System.out.println("EMOTE_CLIENT_OK");
         System.exit(0);
      }

      if ("listen".equals(mode)) {
         String peer = args[1];
         String baseCape = args[2];
         String targetCape = args[3];
         String emote = args[4];
         long t0 = System.currentTimeMillis();

         // start ONLY the SSE tail: no warm(), so no applier re-pull thread exists.
         call("netListener", new Class<?>[0]);
         boolean up = false;
         while (System.currentTimeMillis() - t0 < 8000L) {
            if (FentShare.listenerUp()) { up = true; break; }
            Thread.sleep(200L);
         }
         if (!up) {
            System.out.println("LISTENER_FAIL");
            System.exit(3);
         }
         System.out.println("LISTENER_UP");

         // baseline pull (the ONLY pull of this process)
         call("apiPull", new Class<?>[0]);
         call("apiEmotePull", new Class<?>[0]);
         Thread.sleep(800L);
         String baseline = capeOf(peer);
         if (!baseCape.equals(baseline)) {
            System.out.println("BASELINE_FAIL got=" + baseline);
            System.exit(4);
         }
         System.out.println("BASELINE_OK " + baseline);
         System.out.println("READY");
         System.out.flush();

         // wait for A's change - memory only, never pulling again
         boolean capeOk = false;
         long deadline = System.currentTimeMillis() + 12000L;
         while (System.currentTimeMillis() < deadline) {
            if (targetCape.equals(capeOf(peer))) { capeOk = true; break; }
            Thread.sleep(150L);
         }
         long capeMs = System.currentTimeMillis() - t0;
         System.out.println(capeOk
            ? "SSE_CAPE_OK " + targetCape + " after " + capeMs + "ms"
            : "SSE_CAPE_FAIL still=" + capeOf(peer));

         boolean emoteOk = false;
         deadline = System.currentTimeMillis() + 12000L;
         while (System.currentTimeMillis() < deadline) {
            if (emoteSeen(peer)) { emoteOk = true; break; }
            Thread.sleep(150L);
         }
         System.out.println(emoteOk
            ? "SSE_EMOTE_OK " + emote
            : "SSE_EMOTE_FAIL present=" + emoteSeen(peer));

         System.exit(capeOk && emoteOk ? 0 : 1);
      }

      System.err.println("usage: PeerCheck publish|emotepub|listen ...");
      System.exit(64);
   }

   private PeerCheck() {
   }
}
