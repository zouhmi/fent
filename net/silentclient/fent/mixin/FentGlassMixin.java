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
            // One bg/hover-bg parse per node per frame: kind() classifies with
            // the precomputed fill and surface() reuses it instead of parsing again.
            int fill = FentGlass.fillOf(node, hover);
            int kind = FentGlass.kind(node, hover, renderBridge.screenWidth(), renderBridge.screenHeight(), fill);
            if (kind == FentGlass.PANE) {
               frame.keys = FentGlass.paneKeys();
               FentGlass.paneShadow(renderBridge, node);
               FentGlass.paneGradient(renderBridge, node);
               FentGlass.markPane(node, frame.saved);
            } else if (kind == FentGlass.SURFACE) {
               frame.keys = FentGlass.surfaceKeys();
               if (FentGlass.surface(node, hover, frame.saved, fill) == null) {
                  frame.keys = null;
               }
            }
          } catch (Throwable throwable) {
             // frame.keys is assigned BEFORE mutating, so a mid-mark throw
             // still restores the keys that were saved (shared saved[] only
             // holds originals for frame.keys, never a mix of sets).
             try {
                if (frame.iconTintApplied) {
                   FentGlass.restore(node, frame.iconTintSaved, FentGlass.iconTintKeys());
                }
                if (frame.keys != null) {
                   FentGlass.restore(node, frame.saved, frame.keys);
                }
             } catch (Throwable ignored) {
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
         // Restore onto the node the state was SAVED FROM, even when the pop
         // does not line up with this invocation's node. An exception thrown
         // between HEAD and RETURN used to skip the restore entirely here,
         // permanently leaving glass/gradient attributes on the node - which
         // corrupted menu backdrops (white server-menu cards). saved[] belongs
         // to frame.node, so restoring it is always the correct repair.
         d target = frame.node != null ? frame.node : node;
         if (target == null) {
            return;
         }
         if (frame.iconTintApplied) {
            FentGlass.restore(target, frame.iconTintSaved, FentGlass.iconTintKeys());
         }
         if (frame.keys != null) {
            FentGlass.restore(target, frame.saved, frame.keys);
         }
      } catch (Throwable ignored) {
         // never let a restore failure break rendering of this screen.
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
      // Only cache positive hits: menus build their trees incrementally, so a
      // first-frame miss (anchor not attached yet) must not stick forever and
      // grey out icons on the real main menu a frame later.
      if (mainMenu) {
         FENT_MAIN_MENU_CACHE.put(root, Boolean.TRUE);
      }
      return mainMenu;
   }

   @Unique
   private static final class Frame {
      private d node;
      private final String[] saved = new String[net.silentclient.fent.FentGlass.paneKeys().length];
      private final String[] iconTintSaved = new String[net.silentclient.fent.FentGlass.iconTintKeys().length];
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
            restoreSafely(this.frames[--this.size]);
            this.frames[this.size].clear();
         }
      }

      /** Wiping a frame must not strand fent's attributes on its node. */
      private static void restoreSafely(Frame frame) {
         try {
            if (frame == null || frame.node == null) {
               return;
            }
            if (frame.iconTintApplied) {
               FentGlass.restore(frame.node, frame.iconTintSaved, FentGlass.iconTintKeys());
            }
            if (frame.keys != null) {
               FentGlass.restore(frame.node, frame.saved, frame.keys);
            }
         } catch (Throwable ignored) {
         }
      }
   }
}
