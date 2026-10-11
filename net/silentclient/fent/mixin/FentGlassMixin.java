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

// Every menu panel is painted by the same node painter in net.silentclient.internal.K.g, and this is
// where Aero decides what each node is and hands it the material.
//
// A pane is marked as glass and gets the engine's own Liquid Glass pass, with a fill this mod writes
// instead of the theme's surface colour - the shipped surface token is nearly opaque, which is what
// used to hide the blurred backdrop the menu already renders. The base to accent gradient goes down
// just before the pane, because a glass node returns from the painter before the engine's gradient
// step is reached.
//
// Every other plate keeps its own fill and, for the length of its own draw, gets the same gradient
// written as node attributes. That is the whole trick: the painter resolves "gradient-from" ahead of
// a plain fill, so the node is graded by the engine, rounded by the engine, hovered by the engine -
// this only chooses the two stops, from the colour the node is about to be filled with, hover colour
// included. Both sets of attributes are put back on the way out, so no other theme ever sees them and
// a node that is drawn twice in one frame is graded from its own state each time.
@Mixin(targets = "net.silentclient.internal.K.g")
public abstract class FentGlassMixin {
   // Pushed for every node, changed or not, so every draw pushes exactly one entry and pops one.
   @Unique
   private static final ThreadLocal<Deque<Object[]>> FENT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

   // The "nothing was written" entry. It has to be an array and not null, because ArrayDeque.push
   // throws on a null element and a push of null stopped every unmaterialed draw dead in its
   // tracks, which struck the render out from under the screen entirely.
   @Unique
   private static final Object[] FENT_EMPTY = new Object[0];

   @Unique
   private static final int FENT_MAX_DEPTH = 64;

   @Inject(
      method = "a(Lnet/silentclient/internal/K/f;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("HEAD")
   )
   private static void fent$material(f node, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      // A RETURN injection does not run when a draw throws, and a template that throws half way down a
      // tree would leave this stack deeper than it should be. The depth is capped so a broken frame
      // cannot grow it without end; the attributes it holds are put back on the next clean frame.
      Deque<Object[]> stack = FENT_STACK.get();
      if (stack.size() >= FENT_MAX_DEPTH) {
         stack.clear();
      }
      Object[] shot = FENT_EMPTY;
      if (node != null && renderBridge != null && FentAero.glass()) {
         int kind = FentGlass.kind(node, hover, renderBridge.screenWidth(), renderBridge.screenHeight());
         if (kind == FentGlass.PANE) {
            // Drawn before the pane, so the shadow lands under it and the gradient under its fill.
            FentGlass.paneShadow(renderBridge, node);
            FentGlass.paneGradient(renderBridge, node);
            shot = new Object[] {FentGlass.markPane(node), FentGlass.paneKeys()};
         } else if (kind == FentGlass.SURFACE) {
            String[] saved = FentGlass.surface(node, hover);
            if (saved != null) {
               shot = new Object[] {saved, FentGlass.surfaceKeys()};
            }
         }
      }
      stack.push(shot);
   }

   @Inject(
      method = "a(Lnet/silentclient/internal/K/f;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("RETURN")
   )
   private static void fent$restore(f node, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      Deque<Object[]> stack = FENT_STACK.get();
      while (stack.size() >= FENT_MAX_DEPTH) {
         // Rebuilding from the last clean frame: drop the overflow rather than restoring it.
         stack.pop();
      }
      Object[] shot = stack.isEmpty() ? FENT_EMPTY : stack.pop();
      if (node == null || shot == FENT_EMPTY || shot.length == 0) {
         return;
      }
      FentGlass.restore(node, (String[])shot[0], (String[])shot[1]);
   }
}
