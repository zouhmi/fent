package net.silentclient.fent;

import java.util.Map;
import net.silentclient.common.api.bridge.RenderBridge;
import net.silentclient.internal.K.f;

// The Aero material.
//
// The client has one glass painter, net.silentclient.internal.x.e: a translucent tint, a white
// sheen that decays down from the top edge, and a hairline rim. The menu windows reach it through
// the node painter's "glass" attribute; nothing in the HUD renderer ever asks for it, which is why
// the in-game widgets stay flat under Aero.
//
// It is used two ways here. The menu keeps the node attributes and gains the shadow the node
// painter skips for glass nodes, and the HUD calls the painter directly on top of the module's own
// plate, so only the sheen and the rim are added and legibility over the world does not change.
//
// Cost is the reason both of them are picky about what counts as glass: the painter runs eight
// clipped passes for the sheen and up to eight more for a two colour rim, so a screen with a pane
// is a handful of draws and a screen with fifty cards is a stall. Glassing every card is what froze
// the cosmetics menu, so the rule is one pane per window instead.
public final class FentGlass {
   // Menu panes: the windows, the dialogs, and the few large plates. Cards, rows, wells, chips and
   // pills stay flat - a card is a couple of percent of the screen, a pane is tens of percent.
   private static final float MIN_PANE_RADIUS = 3.8f;
   private static final float MIN_PANE_HEIGHT = 70.0f;
   private static final float MIN_PANE_AREA = 0.07f;
   // A side rail is long and thin: a fifth of the area but most of the screen's height. Whatever
   // passes on length has to be a real plate as well, so a portrait cosmetic card - tall, but not
   // half the screen and not a tenth of its area - still stays flat.
   private static final float PANE_SPAN = 0.45f;
   private static final float MIN_RAIL_AREA = 0.03f;
   private static final String[] KEYS = new String[] {"glass", "glass-highlight", "border-width", "border", "glass-border-bottom"};

   // The menu gets close to full strength; a full strength sheen over the world reads as a white
   // stripe, so the HUD is held back.
   private static final float MENU_HIGHLIGHT = 0.85f;
   private static final float HUD_HIGHLIGHT = 0.6f;
   private static final int RIM_WIDTH = 1;

   private static boolean broken;
   private static boolean warned;

   // A window or dialog pane, or a plate large enough to read as one. Full screen nodes are left
   // alone: the scrim behind a dialog is a plate too, and frosting it would tint the whole world.
   public static boolean isPane(f node, float screenWidth, float screenHeight) {
      if (node == null) {
         return false;
      }
      Map<String, String> attributes = node.b;
      if (attributes.containsKey("glass")
         || attributes.containsKey("gradient-from")
         || attributes.containsKey("glow-color")
         || attributes.containsKey("skeleton")) {
         return false;
      }
      if ((node.b("bg", 0) >>> 24) < 0x04) {
         return false;
      }
      String classes = attributes.get("class");
      if (classes != null && classes.contains("scrim")) {
         return false;
      }
      if (screenWidth > 2.0f && screenHeight > 2.0f && node.h >= screenWidth - 4.0f && node.i >= screenHeight - 4.0f) {
         return false;
      }
      if (node.h < 16.0f || node.i < 12.0f) {
         return false;
      }
      boolean window = classes != null && (classes.contains("window") || classes.contains("dialog"));
      if (!window) {
         // Anything else has to be big: this is what keeps a list of fifty cosmetic cards out.
         if (node.i < MIN_PANE_HEIGHT) {
            return false;
         }
         if (screenWidth > 2.0f && screenHeight > 2.0f) {
            float area = node.h * node.i;
            boolean wide = area >= screenWidth * screenHeight * MIN_PANE_AREA;
            boolean long_ = area >= screenWidth * screenHeight * MIN_RAIL_AREA
               && (node.h >= screenWidth * PANE_SPAN || node.i >= screenHeight * PANE_SPAN);
            if (!wide && !long_) {
               return false;
            }
         }
      }
      return radius(node) >= MIN_PANE_RADIUS;
   }

   // Marks a node as glass. The rim replaces whatever border the theme gave the node, and the
   // bottom of it is a shade darker than the top, which is what the painter turns into a lit edge.
   public static String[] markPane(f node) {
      String[] saved = new String[KEYS.length];
      for (int index = 0; index < KEYS.length; ++index) {
         saved[index] = node.b.get(KEYS[index]);
      }
      node.b.put("glass", "true");
      node.b.put("glass-highlight", Float.toString(MENU_HIGHLIGHT));
      node.b.put("border-width", Integer.toString(RIM_WIDTH));
      node.b.put("border", hex(mix(0xFFFFFF, FentAero.accent(), 0.18f), 0x50));
      node.b.put("glass-border-bottom", hex(0x000000, 0x3D));
      return saved;
   }

   public static void unmarkPane(f node, String[] saved) {
      if (node == null || saved == null) {
         return;
      }
      for (int index = 0; index < KEYS.length && index < saved.length; ++index) {
         if (saved[index] == null) {
            node.b.remove(KEYS[index]);
         } else {
            node.b.put(KEYS[index], saved[index]);
         }
      }
   }

   // The drop shadow under a menu pane. The node painter returns before its own shadow step once a
   // node is glass, and a pane with no shadow is the part that reads as a printed rectangle rather
   // than a sheet floating over the backdrop.
   public static void paneShadow(RenderBridge bridge, f node) {
      if (bridge == null || node == null) {
         return;
      }
      bridge.drawShadow(
         node.f,
         node.g + 3.0f,
         node.h,
         node.i,
         radius(node),
         10.0f,
         FentAero.light() ? 0x33000000 : 0x4D000000
      );
   }

   // The in-game material: sheen and rim over a plate the module already painted. The plate keeps
   // its own colour and alpha, so a widget that is meant to be read over the world still is; what
   // is added is the lit top edge and the hairline, which is what says glass. Strength follows the
   // plate's alpha, so a module fading in or out brings its glass with it, and a plate that is
   // itself light gets a darker edge instead of a white one that would not show against it.
   public static void hud(RenderBridge bridge, float x, float y, float w, float h,
                          float r0, float r1, float r2, float r3, int plate) {
      if (bridge == null || w < 4.0f || h < 4.0f || broken) {
         return;
      }
      float amount = (plate >>> 24 & 0xFF) / 255.0f;
      if (amount <= 0.02f) {
         return;
      }
      int accent = FentAero.accent();
      boolean lightPlate = luminance(plate & 0xFFFFFF) > 0.62f;
      int rim = lightPlate
         ? argb(Math.round(0x3D * amount), mix(0x000000, accent, 0.18f))
         : argb(Math.round(0x48 * amount), mix(0xFFFFFF, accent, 0.22f));
      if ((rim >>> 24) == 0) {
         return;
      }
      try {
         net.silentclient.internal.x.e.a(
            bridge, x, y, w, h, r0, r1, r2, r3,
            0, RIM_WIDTH, rim, rim, HUD_HIGHLIGHT * amount
         );
      } catch (Throwable throwable) {
         broken = true;
         if (!warned) {
            warned = true;
            System.err.println("[Fent] the HUD glass painter moved: " + throwable);
         }
      }
   }

   // Largest corner radius the node asks for, after the token scale has been resolved.
   public static float radius(f node) {
      return Math.max(
         node.c("rounded", 0.0f),
         Math.max(
            Math.max(node.c("rounded-tl", 0.0f), node.c("rounded-tr", 0.0f)),
            Math.max(node.c("rounded-br", 0.0f), node.c("rounded-bl", 0.0f))
         )
      );
   }

   // #RRGGBBAA, which is the order the node painter reads.
   private static String hex(int rgb, int alpha) {
      return String.format("#%06X%02X", Integer.valueOf(rgb & 0xFFFFFF), Integer.valueOf(alpha & 0xFF));
   }

   static int argb(int alpha, int rgb) {
      return (alpha & 0xFF) << 24 | rgb & 0xFFFFFF;
   }

   static float luminance(int rgb) {
      return (0.299f * (rgb >> 16 & 0xFF) + 0.587f * (rgb >> 8 & 0xFF) + 0.114f * (rgb & 0xFF)) / 255.0f;
   }

   static int mix(int from, int to, float amount) {
      float t = amount < 0.0f ? 0.0f : (amount > 1.0f ? 1.0f : amount);
      int red = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
      int green = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
      int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
      return red << 16 | green << 8 | blue;
   }

   private FentGlass() {
   }
}
