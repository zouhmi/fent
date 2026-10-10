package net.silentclient.fent.mixin;

import java.util.ArrayDeque;
import java.util.Deque;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentAero;
import net.silentclient.fent.FentGlass;
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
// While Aero is up this marks the pane shaped nodes as glass, which is what gives the windows and
// dialogs the material, and it puts the attributes back on the way out so no other theme sees them.
// Only panes: the painter spends eight clipped passes on the sheen and eight more on a two colour
// rim, so marking every card on the cosmetics screen is hundreds of draws a frame and a frozen
// game. Cards, rows, wells and chips keep their flat fills by design, which is also what makes the
// panes read as glass.
@Mixin(targets = "net.silentclient.internal.K.g")
public abstract class FentGlassMixin {
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
      if (node != null && FentAero.glass()
         && FentGlass.isPane(node, renderBridge == null ? 0.0f : renderBridge.screenWidth(),
            renderBridge == null ? 0.0f : renderBridge.screenHeight())) {
         // Drawn before the pane, so the shadow lands under it.
         FentGlass.paneShadow(renderBridge, node);
         saved = FentGlass.markPane(node);
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
      FentGlass.unmarkPane(node, saved);
   }
}
