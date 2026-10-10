package net.silentclient.fent.mixin;

import net.fabricmc.loader.api.FabricLoader;
import net.silentclient.fent.FentUnlocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// k() is the step that finishes a config load, after the settings are applied, so it is
// the first point where the Unlocker values are real. One line there names the build that
// is running and what is switched on, which makes a stale jar obvious in latest.log.
@Mixin(targets = "net.silentclient.internal.D.d")
public abstract class FentLogMixin {
   @Unique
   private static boolean fent$logged;

   @Inject(method = "k()V", at = @At("TAIL"))
   private void fent$logOnce(CallbackInfo ci) {
      if (fent$logged) {
         return;
      }
      fent$logged = true;
      String version = FabricLoader.getInstance()
         .getModContainer("zouhmifent")
         .map(container -> container.getMetadata().getVersion().getFriendlyString())
         .orElse("?");
      System.out.println("[Fent] " + version + " loaded, toggles: " + FentUnlocks.summary());
   }
}
