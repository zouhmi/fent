package net.silentclient.fent.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.silentclient.internal.K.i")
public abstract class FentAvatarMixin {
   @Unique
   private static boolean fent$avatarWarned;

   @Unique
   private static final java.util.Set<String> FENT_PRIMED =
      java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

   @Inject(method = "m(Ljava/lang/String;)Ljava/lang/String;", at = @At("HEAD"))
   private static void fent$primeAvatar(String username, CallbackInfoReturnable<String> cir) {
      String key = username == null || username.isEmpty() ? "Steve" : username;
      if (!FENT_PRIMED.add(key)) {
         return;
      }
      if (FENT_PRIMED.size() > 256) {
         FENT_PRIMED.clear();
         FENT_PRIMED.add(key);
      }
      try {
         String url = net.silentclient.internal.K.i.c(key);
         if (url == null || url.isEmpty() || !url.startsWith("http")) {
            return;
         }
         net.silentclient.internal.as.a.a(url);
      } catch (Throwable throwable) {
         if (!fent$avatarWarned) {
            fent$avatarWarned = true;
            System.err.println("[Fent] could not request an account avatar, the account keeps the "
               + "bundled head: " + throwable);
         }
      }
   }
}
