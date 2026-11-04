import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.silentclient.common.api.services.cosmetics.CosmeticType;
import net.silentclient.common.api.services.cosmetics.EquippedCosmetics;
import net.silentclient.fent.FentShare;
import net.silentclient.fent.mixin.FentSharedCosmeticsMixin;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Two-client fent-vs-silentclient cosmetic test. Both JVMs load the REAL
 * SilentClient classes (client-runtime) AND the REAL fent classes; the merge
 * executed on the viewer side is the exact shipped mixin handler
 * FentSharedCosmeticsMixin.fent$mergeSharedCosmetics, invoked directly.
 *
 *   FentVsSilentCheck publish   - CLIENT A: publish fent-side cosmetics as alice
 *   FentVsSilentCheck view      - CLIENT B: pull alice's fent row, then show what
 *                                 a player would actually see when SilentClient's
 *                                 native view and fent's shared view are merged.
 *
 * Verdict per slot: FENT wins overlaps, SILENTCLIENT fills slots fent lacks,
 * SILENTCLIENT untouched when the peer is not on the fent API.
 */
public final class FentVsSilentCheck {
   private static int passed;
   private static int failed;
   private static final StringBuilder failures = new StringBuilder();

   private static void check(String name, boolean ok, Object detail) {
      if (ok) { passed++; System.out.println("  PASS  " + name); }
      else { failed++; failures.append(name).append(" :: ").append(detail).append('\n');
             System.out.println("  FAIL  " + name + "  " + detail); }
   }

   /** Runs the real mixin handler against a real EquippedCosmetics. */
   @SuppressWarnings("unchecked")
   private static Map<CosmeticType, String> mergeLikeGame(String viewerSeesUuid,
                                                          Map<CosmeticType, String> silentClientItems) throws Exception {
      // subclass implements the @Shadow getUuid() the handler calls on `this`
      final class MergeHost extends FentSharedCosmeticsMixin {
         private final String uuid;
         MergeHost(String uuid) { this.uuid = uuid; }
         @Override public String getUuid() { return uuid; }
      }
      MergeHost host = new MergeHost(viewerSeesUuid);
      Constructor<CallbackInfoReturnable> ctor =
         CallbackInfoReturnable.class.getConstructor(String.class, boolean.class, Object.class);
      // cancellable=true: the shipped @Inject declares cancellable = true
      CallbackInfoReturnable<Map<CosmeticType, String>> cir =
         (CallbackInfoReturnable<Map<CosmeticType, String>>)
            ctor.newInstance("getItems", true, silentClientItems);
      Method handler = FentSharedCosmeticsMixin.class
         .getDeclaredMethod("fent$mergeSharedCosmetics", CallbackInfoReturnable.class);
      handler.setAccessible(true);
      handler.invoke(host, cir);
      return cir.getReturnValue();
   }

   private static String get(Map<CosmeticType, String> map, CosmeticType type) {
      return map == null ? null : map.get(type);
   }

   public static void main(String[] args) throws Exception {
      String mode = args[0];
      String base = System.getProperty("fent.api.url", "http://127.0.0.1:6767");

      if ("publish".equals(mode)) {
         // CLIENT A (alice): fent-side selections; in-game these come from
         // FentShare.snapshot() (= fent.selected.* override, else SC selection).
         Map<CosmeticType, String> fentItems = new EnumMap<CosmeticType, String>(CosmeticType.class);
         fentItems.put(CosmeticType.CAPE, "FENT01");
         fentItems.put(CosmeticType.WINGS, "FENT02");
         Method apiPublish = FentShare.class.getDeclaredMethod("apiPublish", Map.class);
         apiPublish.setAccessible(true);
         apiPublish.invoke(null, fentItems);
         String row = httpGet(base + "/api/cosmetics/alice");
         boolean ok = row != null && row.contains("FENT01") && row.contains("FENT02");
         System.out.println(ok ? "CLIENT_A_PUBLISH_OK " + row : "CLIENT_A_PUBLISH_FAIL " + row);
         System.exit(ok ? 0 : 1);
      }

      if ("view".equals(mode)) {
         // CLIENT B (bob): load alice's fent row through the real sync engine
         Method apiPull = FentShare.class.getDeclaredMethod("apiPull");
         apiPull.setAccessible(true);
         apiPull.invoke(null);
         Thread.sleep(1000L);

         Map<CosmeticType, String> shared = FentShare.remoteItems("alice");
         check("client B received alice's fent row via API",
            shared != null && "FENT01".equals(shared.get(CosmeticType.CAPE)), shared);

         // B's SilentClient's OWN native view of alice: cape + hat (no wings)
         Map<CosmeticType, String> scView = new EnumMap<CosmeticType, String>(CosmeticType.class);
         scView.put(CosmeticType.CAPE, "SCAPE");
         scView.put(CosmeticType.HAT, "SCHAT");

         Map<CosmeticType, String> shown = mergeLikeGame("alice", scView);

         System.out.println();
         System.out.println("What the viewer actually sees for alice:");
         for (CosmeticType t : CosmeticType.class.getEnumConstants()) {
            String sc = scView.get(t);
            String fent = shared == null ? null : shared.get(t);
            String out = shown.get(t);
            String winner = "FENT".equals(out) || (fent != null && fent.equals(out)) && fent != null ? "FENT"
               : (sc != null && sc.equals(out) ? "SILENTCLIENT" : "-");
            if (fent != null && fent.equals(out)) winner = "FENT";
            else if (sc != null && sc.equals(out)) winner = "SILENTCLIENT";
            else if (out != null) winner = "?";
            else winner = "-";
            if (!"-".equals(winner) || sc != null || fent != null) {
               System.out.printf("  %-8s  sc=%-8s fent=%-8s -> shown=%-8s winner=%s%n",
                  t, sc, fent, out, winner);
            }
         }
         System.out.println();

         check("OVERLAP slot (cape): FENT value wins",
            "FENT01".equals(get(shown, CosmeticType.CAPE)), get(shown, CosmeticType.CAPE));
         check("FENT-ONLY slot (wings): fent cosmetic shows",
            "FENT02".equals(get(shown, CosmeticType.WINGS)), get(shown, CosmeticType.WINGS));
         check("SC-ONLY slot (hat): silentclient cosmetic preserved",
            "SCHAT".equals(get(shown, CosmeticType.HAT)), get(shown, CosmeticType.HAT));
         check("merge result is immutable (as shipped)", isUnmodifiable(shown), "mutable");

         // peer NOT on the fent api -> silentclient view must pass through untouched
         Map<CosmeticType, String> carolSc = new EnumMap<CosmeticType, String>(CosmeticType.class);
         carolSc.put(CosmeticType.CAPE, "SC01");
         Map<CosmeticType, String> carolShown = mergeLikeGame("carol", carolSc);
         check("peer not on fent: SILENTCLIENT cosmetics show untouched",
            "SC01".equals(get(carolShown, CosmeticType.CAPE)) && carolShown.size() == 1, carolShown);

         // peer WITH a fent row but empty items -> early-return, no clobber
         httpPut(base + "/api/cosmetics/dave", "{\"name\":\"dave\",\"items\":{}}");
         apiPull.invoke(null);
         Thread.sleep(600L);
         Map<CosmeticType, String> daveSc = new EnumMap<CosmeticType, String>(CosmeticType.class);
         daveSc.put(CosmeticType.CAPE, "SC01");
         Map<CosmeticType, String> daveShown = mergeLikeGame("dave", daveSc);
         check("empty fent row does not erase silentclient's value",
            "SC01".equals(get(daveShown, CosmeticType.CAPE)) && daveShown.size() == 1, daveShown);

         System.out.println();
         System.out.println(passed + " passed, " + failed + " failed");
         System.exit(failed == 0 ? 0 : 1);
      }

      System.err.println("usage: FentVsSilentCheck publish|view");
      System.exit(64);
   }

   private static boolean isUnmodifiable(Map<CosmeticType, String> map) {
      try { map.put(CosmeticType.CAPE, "x"); return false; }
      catch (UnsupportedOperationException expected) { return true; }
      catch (Throwable other) { return false; }
   }

   private static String httpGet(String url) {
      return http("GET", url, null);
   }

   private static void httpPut(String url, String json) {
      http("PUT", url, json);
   }

   private static String http(String method, String url, String json) {
      try {
         java.net.http.HttpClient httpc = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5)).build();
         java.net.http.HttpRequest.Builder rb = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
            .timeout(java.time.Duration.ofSeconds(8));
         if ("PUT".equals(method)) {
            rb.header("Content-Type", "application/json")
              .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(json, java.nio.charset.StandardCharsets.UTF_8));
         } else {
            rb.GET();
         }
         java.net.http.HttpResponse<String> r = httpc.send(rb.build(),
            java.net.http.HttpResponse.BodyHandlers.ofString());
         return r.statusCode() >= 400 ? null : r.body();
      } catch (Throwable t) {
         return null;
      }
   }

   private FentVsSilentCheck() {
   }
}
