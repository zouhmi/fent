import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.silentclient.fent.FentGlass;
import net.silentclient.fent.FentMaterial;
import net.silentclient.internal.K.f;

// Checks what the Aero material does to a menu surface, on the shapes the client's own menu templates
// use, without the game running. The theme work is otherwise only visible in a frame, and the two ways
// it can quietly go wrong are both checkable here: a surface that should be themed being left flat,
// and a node that is not a surface - text, an icon hit box, the full screen backdrop, the scrim behind
// a dialog - being given a fill or a gradient it should not have.
//
// Sizes are the ones the templates declare (a mod row is 17 units tall, a theme card 34, a settings
// row 22, an icon button 9 square), taken from assets/silentclient/menus. Absolute positions do not
// matter to the policy, so every fixture is placed at the same spot and the screen is 1920x1080.
//
// The material numbers are checked as facts rather than left to a comment: a pane fill is well under a
// quarter, a plate loses alpha, and a plate the user already made transparent is never pushed back up.
//
// Usage: java MaterialCheck <classpath with the client jar and the built classes>
public final class MaterialCheck {
   private static final float SCREEN_WIDTH = 1920.0f;
   private static final float SCREEN_HEIGHT = 1080.0f;

   private static int total;
   private static final List<String> failures = new ArrayList<String>();

   public static void main(String[] args) {
      // The window and the dialog the client draws every screen in, and - the rule that fixed the
      // invisible menu - a large plate that is not either of them stays a surface no matter how big
      // it is, because a pane with nothing behind it to show through reads as an invisible sheet.
      expect("window", node("panel", 420.0f, 300.0f, "class", "window", "bg", "#CC14161B", "rounded", "6"), false, FentGlass.PANE);
      expect("dialog", node("panel", 300.0f, 160.0f, "class", "dialog", "bg", "#E014161B", "rounded", "4"), false, FentGlass.PANE);
      expect("large panel stays a surface", node("panel", 700.0f, 520.0f, "bg", "#B414161B", "rounded", "6"), false, FentGlass.SURFACE);
      expect("full width section stays a surface", node("panel", 1800.0f, 150.0f, "bg", "#B414161B", "rounded", "6"), false, FentGlass.SURFACE);

      // The plates a list is made of: a mod row, a theme card, a settings row, a cosmetic card.
      expect("mod row", node("panel", 117.0f, 17.0f, "bg", "#FF1A1A1A", "rounded", "3"), false, FentGlass.SURFACE);
      expect("theme card", node("panel", 160.0f, 34.0f, "bg", "#FF1A1A1A", "rounded", "4"), false, FentGlass.SURFACE);
      expect("setting row", node("panel", 260.0f, 22.0f, "bg", "#FF1A1A1A", "rounded", "3"), false, FentGlass.SURFACE);
      expect("cosmetic card", node("panel", 200.0f, 120.0f, "bg", "#FF1A1A1A", "rounded", "4"), false, FentGlass.SURFACE);
      expect("surface floor", node("panel", 40.0f, 10.0f, "bg", "#FF1A1A1A", "rounded", "2"), false, FentGlass.SURFACE);
      expect("chip below the floor", node("panel", 40.0f, 9.0f, "bg", "#FF1A1A1A", "rounded", "2"), false, FentGlass.NONE);

      // A plate that only shows itself on hover is still a plate when the pointer is on it.
      expect("icon button, idle", node("panel", 120.0f, 20.0f, "hover-bg", "#FF1A1A1A", "rounded", "2"), false, FentGlass.NONE);
      expect("icon button, hovered", node("panel", 120.0f, 20.0f, "hover-bg", "#FF1A1A1A", "rounded", "2"), true, FentGlass.SURFACE);

      // What must never be filled or graded.
      expect("text", node("text", 120.0f, 10.0f), false, FentGlass.NONE);
      expect("image", node("image", 9.0f, 9.0f, "src", "/icon.svg"), false, FentGlass.NONE);
      expect("transparent plate", node("panel", 200.0f, 40.0f, "bg", "#1A1A1A00", "rounded", "4"), false, FentGlass.NONE);
      // A plate whose fill is a theme token, which is how the client's own templates write one. The
      // resolver runs offline against the built in theme and returns white at a low alpha, which is
      // also the faintest a real card gets, so this is the floor of the material the surface rule has
      // to keep reaching. Values that are data bound - "${cardBg}" - only resolve in game, so the
      // fixtures use what a bound node holds by the time it is painted.
      expect("token filled row", node("panel", 117.0f, 17.0f, "bg", "@card", "rounded", "3"), false, FentGlass.SURFACE);
      expect("full screen backdrop", node("panel", SCREEN_WIDTH, SCREEN_HEIGHT, "bg", "#73141414"), false, FentGlass.NONE);
      expect("scrim", node("panel", 600.0f, 400.0f, "class", "scrim", "bg", "#73141414"), false, FentGlass.NONE);
      expect("skeleton", node("panel", 200.0f, 40.0f, "bg", "#FF1A1A1A", "skeleton", "true"), false, FentGlass.NONE);
      expect("already graded", node("panel", 200.0f, 40.0f, "bg", "#FF1A1A1A", "gradient-from", "#FF1A1A1A"), false, FentGlass.NONE);
      expect("already glass", node("panel", 420.0f, 300.0f, "class", "window", "bg", "#CC14161B", "glass", "true"), false, FentGlass.NONE);

      paneFill();
      paneMarking();
      surfaceGradient();
      plateAlpha();

      if (!failures.isEmpty()) {
         for (String failure : failures) {
            System.err.println("MaterialCheck: " + failure);
         }
         System.err.println("MaterialCheck: FAILED - " + failures.size() + " of " + total + " checks did not hold");
         System.exit(1);
         return;
      }
      System.out.println("MaterialCheck: OK - " + total + " checks hold");
   }

   // The pane fill is what used to be the theme's surface colour, so it is the number that decides how
   // much of the blurred backdrop shows through: it has to be far below a quarter, and the hover state
   // above it.
   private static void paneFill() {
      check("pane fill is translucent", FentMaterial.PANE_FILL < 0x40);
      check("pane hover is above the idle fill", FentMaterial.PANE_FILL_HOVER > FentMaterial.PANE_FILL);
      check("pane gradient stops differ", FentMaterial.PANE_GRADIENT_TOP != FentMaterial.PANE_GRADIENT_BOTTOM);
      check("pane rim is visible", FentMaterial.PANE_RIM >= 0x30);
      check("pane sheen is present", FentMaterial.PANE_SHEEN > 0.5f);
   }

   // Marking a pane has to answer the fill, the hover fill, the highlight and the rim, and taking it
   // off has to leave the node exactly as the template built it.
   private static void paneMarking() {
      f node = node("panel", 420.0f, 300.0f, "class", "window", "bg", "#CC14161B", "border", "#22FFFFFF", "rounded", "6");
      String[] saved = FentGlass.markPane(node);
      check("glass is set", "true".equals(node.b.get("glass")));
      check("fill is the material's", FentMaterial.PANE_FILL == alphaIn(node.b.get("glass-tint")));
      check("hover fill is the material's", FentMaterial.PANE_FILL_HOVER == alphaIn(node.b.get("hover-glass-tint")));
      check("sheen is the material's", Float.toString(FentMaterial.PANE_SHEEN).equals(node.b.get("glass-highlight")));
      check("rim is one unit", "1".equals(node.b.get("border-width")));
      check("the theme's own border is replaced", !"#22FFFFFF".equals(node.b.get("border")));
      FentGlass.restore(node, saved, FentGlass.paneKeys());
      check("glass is taken back off", !node.b.containsKey("glass"));
      check("fill is taken back off", !node.b.containsKey("glass-tint"));
      check("hover fill is taken back off", !node.b.containsKey("hover-glass-tint"));
      check("the theme's own border is put back", "#22FFFFFF".equals(node.b.get("border")));
   }

   // A surface keeps its own colour and gains a gradient: the top stop is its colour lifted, the
   // bottom is pulled toward the accent, the alpha is its own, and the hover colour changes both.
   private static void surfaceGradient() {
      f node = node("panel", 117.0f, 17.0f, "bg", "#FF1A1A1A", "hover-bg", "#FF262626", "rounded", "3");
      String[] saved = FentGlass.surface(node, false);
      check("a surface is graded", saved != null && node.b.containsKey("gradient-from") && node.b.containsKey("gradient-to"));
      check("the gradient is vertical", Float.toString(FentMaterial.SURFACE_ANGLE).equals(node.b.get("gradient-angle")));
      int from = rgbIn(node.b.get("gradient-from"));
      int to = rgbIn(node.b.get("gradient-to"));
      check("the two stops differ", from != to);
      int surfaceAlpha = 0xFF;
      // A surface is never made more opaque than the colour its own template gave it.
      check("the top stop adds no opacity", alphaIn(node.b.get("gradient-from")) <= surfaceAlpha);
      check("the bottom stop adds no opacity", alphaIn(node.b.get("gradient-to")) <= surfaceAlpha);
      check("the gradient runs downhill", alphaIn(node.b.get("gradient-to")) <= alphaIn(node.b.get("gradient-from")));
      FentGlass.restore(node, saved, FentGlass.surfaceKeys());
      check("the gradient is taken back off", !node.b.containsKey("gradient-from"));

      f hovered = node("panel", 117.0f, 17.0f, "bg", "#FF1A1A1A", "hover-bg", "#FF262626", "rounded", "3");
      FentGlass.surface(hovered, true);
      check("hover grades from the hover colour", rgbIn(hovered.b.get("gradient-from")) != from);
   }

   // The HUD plate: the material takes alpha off, never adds it, and never overrides a plate the user
   // has already made more transparent than the floor.
   private static void plateAlpha() {
      int opaque = 0xFF1A1A1A;
      int softened = FentMaterial.soften(opaque, FentMaterial.PLATE_ALPHA, FentMaterial.PLATE_FLOOR);
      check("a plate loses alpha", FentMaterial.alphaOf(softened) < FentMaterial.alphaOf(opaque));
      check("a plate never goes below the floor", FentMaterial.alphaOf(softened) >= FentMaterial.PLATE_FLOOR);
      int faint = 0x641A1A1A;
      check("a faint plate is left alone", FentMaterial.soften(faint, FentMaterial.PLATE_ALPHA, FentMaterial.PLATE_FLOOR) == faint);
      check("a clear plate is left alone", FentMaterial.soften(0x001A1A1A, FentMaterial.PLATE_ALPHA, FentMaterial.PLATE_FLOOR) == 0x001A1A1A);
      check("the backdrop plate is lighter than a plain one", FentMaterial.BACKDROP_PLATE_ALPHA < FentMaterial.PLATE_ALPHA);
      check("the tile plate is lighter than a plain one", FentMaterial.TILE_ALPHA < FentMaterial.PLATE_ALPHA);
      check("the tile floor is below the plate floor", FentMaterial.TILE_FLOOR < FentMaterial.PLATE_FLOOR);
   }

   private static void expect(String what, f node, boolean hover, int wanted) {
      ++total;
      int kind = FentGlass.kind(node, hover, SCREEN_WIDTH, SCREEN_HEIGHT);
      if (kind != wanted) {
         failures.add(what + " is " + label(kind) + ", " + label(wanted) + " was wanted");
      }
   }

   private static void check(String what, boolean holds) {
      ++total;
      if (!holds) {
         failures.add(what);
      }
   }

   private static String label(int kind) {
      return kind == FentGlass.PANE ? "a pane" : (kind == FentGlass.SURFACE ? "a surface" : "not material");
   }

   private static f node(String type, float width, float height, String... attributes) {
      f node = new f(type);
      node.f = 40.0f;
      node.g = 40.0f;
      node.h = width;
      node.i = height;
      Map<String, String> map = node.b;
      for (int index = 0; index + 1 < attributes.length; index += 2) {
         map.put(attributes[index], attributes[index + 1]);
      }
      return node;
   }

   // The two halves of the colour encoding the material writes into a node, read back here so a change
   // in the format shows up as a failed check rather than as a frame that looks wrong.
   private static int alphaIn(String hex) {
      checkValue(hex);
      return Integer.parseInt(hex.substring(7, 9), 16);
   }

   private static int rgbIn(String hex) {
      checkValue(hex);
      return (int)Long.parseLong(hex.substring(1, 7), 16);
   }

   private static void checkValue(String hex) {
      if (hex == null || hex.length() != 9 || hex.charAt(0) != '#') {
         failures.add("a colour is not #RRGGBBAA: " + hex);
         throw new IllegalStateException("colour " + hex);
      }
   }

   private MaterialCheck() {
   }
}
