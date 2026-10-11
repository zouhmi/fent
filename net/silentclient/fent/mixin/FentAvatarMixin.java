package net.silentclient.fent.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// The account changer's heads never load, and the reason is a closed loop in the client.
//
// net.silentclient.internal.K.i.m(String name) is the avatarUrl the account picker and the account
// rows read, and it only returns the real head once the image is already in the client's own cache:
//
//    return as.a.b(n(name)) ? n(name) : "/assets/silentclient/menus/images/default_head.png";
//
// n(name) is <skins>/avatar/<name>?size=32. as.a.b(url) is true only after as.a has that URL in
// memory, and the only thing that would ask for it is the renderer, which asks for whatever the node
// resolved to - and the node resolved to the bundled default head. So the request is never made,
// as.a.b stays false forever, and every account shows the bundled head. Friends and friend requests
// have no such gate (their avatarUrl is n(name) directly), which is why their heads do load.
//
// The fix is to ask for the image the client would have asked for, with the same key: max-edge is 0
// on the account nodes, so the renderer's own request is as.a.a(url, 0), and priming exactly that
// makes as.a.b(url) true a few hundred milliseconds later. The picker re-resolves avatarUrl every
// frame, so the head then switches from the bundled one to the real one on its own, and the file is
// on disk after the first fetch, so later sessions are instant.
//
// Nothing is downloaded twice: as.a holds an in-flight set and a per URL cooldown, so a repeated
// prime while a fetch runs, or after a failed one, is a no-op until it is worth retrying. If the
// image service is unreachable the call does nothing and the account keeps the bundled head, which
// is what it showed before.
@Mixin(targets = "net.silentclient.internal.K.i")
public abstract class FentAvatarMixin {
   @Unique
   private static boolean fent$avatarWarned;

   // m returns the URL or the bundled head, so the callback has to be the returnable one: Mixin
   // refuses the whole mixin with "CallbackInfoReturnable is required" otherwise, which shows up as
   // a failed apply in the game log, not at build time.
   @Inject(method = "m(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"))
   private static void fent$primeAvatar(String username, CallbackInfoReturnable<String> cir) {
      try {
         // The client's own URL, so the cache key is the same string the renderer will ask for. c(String)
         // is the public accessor for the private n(String) the gate itself calls.
         String url = net.silentclient.internal.K.i.c(
            username == null || username.isEmpty() ? "Steve" : username);
         if (url == null || url.isEmpty() || !url.startsWith("http")) {
            return;
         }
         if (net.silentclient.internal.as.a.b(url)) {
            return;
         }
         net.silentclient.internal.as.a.a(url, 0);
      } catch (Throwable throwable) {
         if (!fent$avatarWarned) {
            fent$avatarWarned = true;
            System.err.println("[Fent] could not request an account avatar, the account keeps the "
               + "bundled head: " + throwable);
         }
      }
   }
}
