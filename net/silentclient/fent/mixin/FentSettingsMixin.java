package net.silentclient.fent.mixin;

import java.util.ArrayList;
import java.util.Collections;
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

// Adds an "Unlocker" page to Modules > Settings.
// p.a is the category button list, p.b holds the rows per category.
@Mixin(targets = "net.silentclient.internal.D.p")
public abstract class FentSettingsMixin {
   @Shadow
   @Final
   @Mutable
   private static List<p.a> a;

   @Shadow
   @Final
   private static Map<String, List<p.b>> b;

   @Inject(method = "<clinit>", at = @At("TAIL"))
   private static void fent$addUnlocker(CallbackInfo ci) {
      List<p.a> categories = new ArrayList<p.a>(a);
      categories.add(new p.a("unlocker", "Unlocker"));
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
      rows.add(fent$toggle("coins.off", "Keep Coins Off"));
      b.put("unlocker", rows);
   }

   // Toggle row: category, key, label, type, default, options, min, max, step.
   @Unique
   private static p.b fent$toggle(String key, String label) {
      return new p.b("unlocker", key, label, "toggle", "true", Collections.<String>emptyList(), 0, 0, 0);
   }
}
