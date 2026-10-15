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

@Mixin(targets = "net.silentclient.internal.K.g")
public abstract class FentGlassMixin {
   @Unique
   private static final ThreadLocal<Deque<Object[]>> FENT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

   @Unique
   private static final Object[] FENT_EMPTY = new Object[0];

   @Unique
   private static final int FENT_MAX_DEPTH = 64;

   @Inject(
      method = "a(Lnet/silentclient/internal/K/f;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("HEAD")
   )
   private static void fent$material(f node, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      Deque<Object[]> stack = FENT_STACK.get();
      if (stack.size() >= FENT_MAX_DEPTH) {
         stack.clear();
      }
      Object[] shot = FENT_EMPTY;
      if (node != null && renderBridge != null && FentAero.glass()) {
         try {
            String[] white = FentGlass.whiten(node);
            if (white != null) {
               shot = new Object[] {white, FentGlass.textKeys()};
            } else {
               int kind = FentGlass.kind(node, hover, renderBridge.screenWidth(), renderBridge.screenHeight());
               if (kind == FentGlass.PANE) {
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
          } catch (Throwable throwable) {
             shot = FENT_EMPTY;
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
      if (stack.size() >= FENT_MAX_DEPTH) {
         stack.clear();
         return;
      }
      Object[] shot = stack.isEmpty() ? FENT_EMPTY : stack.pop();
      if (node == null || shot == FENT_EMPTY || shot.length == 0) {
         return;
      }
      FentGlass.restore(node, (String[])shot[0], (String[])shot[1]);
   }
}
