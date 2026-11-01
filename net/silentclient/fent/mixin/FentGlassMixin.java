package net.silentclient.fent.mixin;

import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.fent.FentAero;
import net.silentclient.fent.FentGlass;
import net.silentclient.internal.L.e;
import net.silentclient.internal.ac.d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentclient.internal.L.f")
public abstract class FentGlassMixin {
   @Unique
   private static final ThreadLocal<FrameStack> FENT_STACK = ThreadLocal.withInitial(FrameStack::new);

   @Unique
   private static final int FENT_MAX_DEPTH = 64;

   @Inject(
      method = "a(Lnet/silentclient/internal/L/e;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("HEAD")
   )
   private static void fent$material(e element, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      d node = element == null ? null : element.a();
      Frame frame = FENT_STACK.get().push();
      frame.node = node;
      if (node != null && renderBridge != null && FentAero.glass()) {
         try {
            String[] white = FentGlass.whiten(node);
            if (white != null) {
               System.arraycopy(white, 0, frame.saved, 0, white.length);
               frame.keys = FentGlass.textKeys();
            } else {
               int kind = FentGlass.kind(node, hover, renderBridge.screenWidth(), renderBridge.screenHeight());
               if (kind == FentGlass.PANE) {
                  FentGlass.paneShadow(renderBridge, node);
                  FentGlass.paneGradient(renderBridge, node);
                  FentGlass.markPane(node, frame.saved);
                  frame.keys = FentGlass.paneKeys();
               } else if (kind == FentGlass.SURFACE) {
                  if (FentGlass.surface(node, hover, frame.saved) != null) {
                     frame.keys = FentGlass.surfaceKeys();
                  }
               }
            }
          } catch (Throwable throwable) {
             frame.clear();
          }
      }
   }

   @Inject(
      method = "a(Lnet/silentclient/internal/L/e;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("RETURN")
   )
   private static void fent$restore(e element, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      d node = element == null ? null : element.a();
      Frame frame = FENT_STACK.get().pop();
      if (frame == null) {
         return;
      }
      try {
         if (node == null || frame.node != node || frame.saved == null || frame.keys == null) {
            return;
         }
         FentGlass.restore(node, frame.saved, frame.keys);
      } finally {
         frame.clear();
      }
   }

   @Unique
   private static final class Frame {
      private d node;
      private final String[] saved = new String[7];
      private String[] keys;

      private void clear() {
         this.node = null;
         for (int index = 0; index < this.saved.length; ++index) {
            this.saved[index] = null;
         }
         this.keys = null;
      }
   }

   @Unique
   private static final class FrameStack {
      private final Frame[] frames = new Frame[FENT_MAX_DEPTH];
      private int size;

      private Frame push() {
         if (this.size >= FENT_MAX_DEPTH) {
            this.clear();
         }
         Frame frame = this.frames[this.size];
         if (frame == null) {
            frame = new Frame();
            this.frames[this.size] = frame;
         }
         frame.clear();
         ++this.size;
         return frame;
      }

      private Frame pop() {
         return this.size == 0 ? null : this.frames[--this.size];
      }

      private void clear() {
         while (this.size > 0) {
            this.frames[--this.size].clear();
         }
      }
   }
}
