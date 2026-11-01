package net.silentclient.fent.mixin;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
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

   @Unique
   private static final Map<e, Boolean> FENT_MAIN_MENU_CACHE =
      Collections.synchronizedMap(new WeakHashMap<e, Boolean>());

   @Inject(
      method = "a(Lnet/silentclient/internal/L/e;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("HEAD")
   )
   private static void fent$material(e element, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      d node = element;
      Frame frame = FENT_STACK.get().push();
      frame.node = node;
      if (node != null && renderBridge != null && FentAero.glass()) {
         try {
            if ("icon-button".equals(node.c) && fent$isMainMenu(element)) {
               FentGlass.tintMainMenuIcon(node, frame.iconTintSaved);
               frame.iconTintApplied = true;
            }
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
             if (frame.iconTintApplied) {
                FentGlass.restore(node, frame.iconTintSaved, FentGlass.iconTintKeys());
             }
             frame.clear();
          }
      }
   }

   @Inject(
      method = "a(Lnet/silentclient/internal/L/e;Lnet/silentclient/common/api/bridge/RenderBridge;ZI)V",
      at = @At("RETURN")
   )
   private static void fent$restore(e element, RenderBridge renderBridge, boolean hover, int background, CallbackInfo ci) {
      d node = element;
      Frame frame = FENT_STACK.get().pop();
      if (frame == null) {
         return;
      }
      try {
         if (node == null || frame.node != node) {
            return;
         }
         if (frame.iconTintApplied) {
            FentGlass.restore(node, frame.iconTintSaved, FentGlass.iconTintKeys());
         }
         if (frame.keys != null) {
            FentGlass.restore(node, frame.saved, frame.keys);
         }
      } finally {
         frame.clear();
      }
   }

   @Unique
   private static boolean fent$isMainMenu(e element) {
      e root = element;
      while (root != null && root.b != null) {
         root = root.b;
      }
      if (root == null) {
         return false;
      }
      Boolean cached = FENT_MAIN_MENU_CACHE.get(root);
      if (cached != null) {
         return cached.booleanValue();
      }
      boolean mainMenu = FentGlass.isMainMenu(root);
      FENT_MAIN_MENU_CACHE.put(root, Boolean.valueOf(mainMenu));
      return mainMenu;
   }

   @Unique
   private static final class Frame {
      private d node;
      private final String[] saved = new String[7];
      private final String[] iconTintSaved = new String[3];
      private String[] keys;
      private boolean iconTintApplied;

      private void clear() {
         this.node = null;
         for (int index = 0; index < this.saved.length; ++index) {
            this.saved[index] = null;
         }
         for (int index = 0; index < this.iconTintSaved.length; ++index) {
            this.iconTintSaved[index] = null;
         }
         this.keys = null;
         this.iconTintApplied = false;
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
