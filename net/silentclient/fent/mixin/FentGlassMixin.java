package net.silentclient.fent.mixin;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentAero;
import net.silentclient.internal.K.f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Every menu panel is painted by the same node painter in net.silentclient.internal.K.g. A node
// carrying the "glass" attribute takes the engine's own Liquid Glass path - translucent tint, a
// white sheen across the top, and a gradient hairline - but no shipped template asks for it, so the
// path is dead and every menu draws flat plates.
//
// While the Aero theme is up this marks the panel shaped nodes as glass, which is what gives the
// account switcher, the cosmetics locker, the settings pages and every window and dialog the
// material, and it puts the attributes back on the way out so no other theme sees them.
@Mixin(targets = "net.silentclient.internal.K.g")
public abstract class FentGlassMixin {
   @Unique
   private static final String[] FENT_KEYS = new String[] {"glass", "glass-highlight", "border-width"};

   // Pushed for nodes that were left alone, so every draw pushes exactly one entry and pops one.
   @Unique
   private static final String[] FENT_NONE = new String[0];

   @Unique
   private static final ThreadLocal<Deque<String[]>> FENT_SAVED = ThreadLocal.withInitial(ArrayDeque::new);

   @Inject(
      method = "a(Lnet/silentclient/internal/K/f;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("HEAD")
   )
   private static void fent$glass(f node, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      String[] saved = null;
      if (node != null && FentAero.glass() && fent$panel(node)) {
         saved = new String[FENT_KEYS.length];
         for (int index = 0; index < FENT_KEYS.length; ++index) {
            saved[index] = node.b.get(FENT_KEYS[index]);
         }
         node.b.put("glass", "true");
         if (saved[1] == null) {
            node.b.put("glass-highlight", "0.55");
         }
         // Most cards ship a hairline of their own width already, but the windows and dialogs ask for
         // @hairline, which reads as zero here, so the rim is set for all of them.
         node.b.put("border-width", "1");
      }
      FENT_SAVED.get().push(saved == null ? FENT_NONE : saved);
   }

   @Inject(
      method = "a(Lnet/silentclient/internal/K/f;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("RETURN")
   )
   private static void fent$unglass(f node, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      Deque<String[]> stack = FENT_SAVED.get();
      String[] saved = stack.isEmpty() ? null : stack.pop();
      if (node == null || saved == null || saved.length == 0) {
         return;
      }
      for (int index = 0; index < FENT_KEYS.length; ++index) {
         if (saved[index] == null) {
            node.b.remove(FENT_KEYS[index]);
         } else {
            node.b.put(FENT_KEYS[index], saved[index]);
         }
      }
   }

   // Only panel shaped nodes: a real background, a card sized corner or larger, and enough of a body
   // to read as a pane. Rows, wells, chips, buttons and pills keep their flat fills, so the glass
   // lands on the windows, dialogs and cards rather than on every plate in the menu.
   @Unique
   private static boolean fent$panel(f node) {
      Map<String, String> attributes = node.b;
      if (attributes.containsKey("glass")) {
         return false;
      }
      String background = attributes.get("bg");
      if (background == null || background.isEmpty()) {
         return false;
      }
      if (attributes.containsKey("gradient-from")
         || attributes.containsKey("glow-color")
         || attributes.containsKey("skeleton")) {
         return false;
      }
      // Only plates that actually carry a fill: a fully transparent one has nothing to frost, and
      // the faint washes the themes use for cards are meant to be seen through.
      if ((node.b("bg", 0) >>> 24) < 0x04) {
         return false;
      }
      // @card is 4 and @panel is 5.33 on the radius scale, @control is 2.67 and @head 1.33.
      float radius = Math.max(
         node.c("rounded", 0.0f),
         Math.max(
            Math.max(node.c("rounded-tl", 0.0f), node.c("rounded-tr", 0.0f)),
            Math.max(node.c("rounded-br", 0.0f), node.c("rounded-bl", 0.0f))
         )
      );
      return radius >= 3.8f && node.h >= 16.0f && node.i >= 12.0f;
   }
}
