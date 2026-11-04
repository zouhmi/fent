package net.silentclient.fent.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.silentclient.internal.D.p;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentclient.internal.D.p")
public abstract class FentSettingsMixin {
   @Shadow
   @Final
   @Mutable
   private static List<p.a> a;

   @Shadow
   @Final
   @Mutable
   private static Map<String, List<p.b>> b;

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$addUnlocker(CallbackInfo ci) {
      List<p.a> categories = new ArrayList<p.a>(a);
      categories.add(new p.a("unlocker", "Fent"));
      a = categories;

      List<p.b> rows = new ArrayList<p.b>();
      rows.add(fent$toggle("cosmetics", "Unlock Cosmetics"));
      rows.add(fent$toggle("cosmetics.cape", "Unlock Capes"));
      rows.add(fent$toggle("cosmetics.wings", "Unlock Wings"));
      rows.add(fent$toggle("cosmetics.hat", "Unlock Hats"));
      rows.add(fent$toggle("cosmetics.bandana", "Unlock Bandanas"));
      rows.add(fent$toggle("cosmetics.shield", "Unlock Shields"));
      rows.add(fent$toggle("cosmetics.icon", "Unlock Icons"));
      rows.add(fent$toggle("cosmetics.pet", "Unlock Pets"));
      rows.add(fent$toggle("cosmetics.aura", "Unlock Auras"));
      rows.add(fent$toggle("emotes", "Unlock Emotes"));
      rows.add(fent$toggle("themes", "Unlock Themes"));
      rows.add(fent$toggle("premium", "Unlock Premium"));
      rows.add(fent$toggle("aero.glass", "Liquid Glass UI"));
      rows.add(fent$toggle("aero.hud", "Liquid Glass HUD"));
      rows.add(fent$toggle("aero.refraction", "Liquid Glass Refraction"));
      rows.add(fent$toggle("colorpicker.off", "Hide Colour Picker"));
      rows.add(fent$toggle("lyrics.cache", "Cache Lyrics"));
      rows.add(fent$toggle("coins.off", "Keep Coins Off"));
      try {
         b = new HashMap<String, List<p.b>>(b);
         b.put("unlocker", rows);
      } catch (Throwable throwable) {
         System.err.println("[Fent] could not add the Unlocker page: " + throwable);
      }
   }

   @Unique
   private static p.b fent$toggle(String key, String label) {
      return new p.b("unlocker", key, label, "toggle", "true", Collections.<String>emptyList(), 0, 0, 0);
   }
}
