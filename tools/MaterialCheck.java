import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.silentclient.fent.FentGlass;
import net.silentclient.fent.FentMaterial;
import net.silentclient.internal.K.f;

public final class MaterialCheck {
   private static final float SCREEN_WIDTH = 1920.0f;
   private static final float SCREEN_HEIGHT = 1080.0f;

   private static int total;
   private static final List<String> failures = new ArrayList<String>();

   public static void main(String[] args) {
      expect("window", node("panel", 420.0f, 300.0f, "class", "window", "bg", "#CC14161B", "rounded", "6"), false, FentGlass.PANE);
      expect("dialog", node("panel", 300.0f, 160.0f, "class", "dialog", "bg", "#E014161B", "rounded", "4"), false, FentGlass.PANE);
      expect("large panel stays a surface", node("panel", 700.0f, 520.0f, "bg", "#B414161B", "rounded", "6"), false, FentGlass.SURFACE);
      expect("full width section stays a surface", node("panel", 1800.0f, 150.0f, "bg", "#B414161B", "rounded", "6"), false, FentGlass.SURFACE);

      expect("mod row", node("panel", 117.0f, 17.0f, "bg", "#FF1A1A1A", "rounded", "3"), false, FentGlass.SURFACE);
      expect("theme card", node("panel", 160.0f, 34.0f, "bg", "#FF1A1A1A", "rounded", "4"), false, FentGlass.SURFACE);
      expect("setting row", node("panel", 260.0f, 22.0f, "bg", "#FF1A1A1A", "rounded", "3"), false, FentGlass.SURFACE);
      expect("cosmetic card", node("panel", 200.0f, 120.0f, "bg", "#FF1A1A1A", "rounded", "4"), false, FentGlass.SURFACE);
      expect("surface floor", node("panel", 40.0f, 10.0f, "bg", "#FF1A1A1A", "rounded", "2"), false, FentGlass.SURFACE);
      expect("chip below the floor", node("panel", 40.0f, 9.0f, "bg", "#FF1A1A1A", "rounded", "2"), false, FentGlass.NONE);

      expect("icon button, idle", node("panel", 120.0f, 20.0f, "hover-bg", "#FF1A1A1A", "rounded", "2"), false, FentGlass.NONE);
      expect("icon button, hovered", node("panel", 120.0f, 20.0f, "hover-bg", "#FF1A1A1A", "rounded", "2"), true, FentGlass.SURFACE);

      expect("text", node("text", 120.0f, 10.0f), false, FentGlass.NONE);
      expect("image", node("image", 9.0f, 9.0f, "src", "/icon.svg"), false, FentGlass.NONE);
      expect("transparent plate", node("panel", 200.0f, 40.0f, "bg", "#1A1A1A00", "rounded", "4"), false, FentGlass.NONE);
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
      textWhite();

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

    private static void paneFill() {
      check("pane fill is translucent", FentMaterial.PANE_FILL < 0x40);
      check("pane hover is above the idle fill", FentMaterial.PANE_FILL_HOVER > FentMaterial.PANE_FILL);
      check("pane gradient stops differ", FentMaterial.PANE_GRADIENT_TOP != FentMaterial.PANE_GRADIENT_BOTTOM);
      check("pane rim is visible", FentMaterial.PANE_RIM >= 0x30);
      check("pane sheen is present", FentMaterial.PANE_SHEEN > 0.5f);
   }

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

    private static void surfaceGradient() {
      f node = node("panel", 117.0f, 17.0f, "bg", "#FF1A1A1A", "hover-bg", "#FF262626", "rounded", "3");
      String[] saved = FentGlass.surface(node, false);
      check("a surface is graded", saved != null && node.b.containsKey("gradient-from") && node.b.containsKey("gradient-to"));
      check("the gradient is vertical", Float.toString(FentMaterial.SURFACE_ANGLE).equals(node.b.get("gradient-angle")));
      int from = rgbIn(node.b.get("gradient-from"));
      int to = rgbIn(node.b.get("gradient-to"));
      check("the two stops differ", from != to);
      int surfaceAlpha = 0xFF;
      check("the top stop adds no opacity", alphaIn(node.b.get("gradient-from")) <= surfaceAlpha);
      check("the bottom stop adds no opacity", alphaIn(node.b.get("gradient-to")) <= surfaceAlpha);
      check("the gradient runs downhill", alphaIn(node.b.get("gradient-to")) <= alphaIn(node.b.get("gradient-from")));
      FentGlass.restore(node, saved, FentGlass.surfaceKeys());
      check("the gradient is taken back off", !node.b.containsKey("gradient-from"));

      f hovered = node("panel", 117.0f, 17.0f, "bg", "#FF1A1A1A", "hover-bg", "#FF262626", "rounded", "3");
      FentGlass.surface(hovered, true);
      check("hover grades from the hover colour", rgbIn(hovered.b.get("gradient-from")) != from);
   }

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

    private static void textWhite() {
      check("plain grey goes white", FentGlass.whiteFor(0xFF8A8F98) == 0xFFFFFFFF);
      check("dim white goes white", FentGlass.whiteFor(0x66FFFFFF) == 0xFFFFFFFF);
      check("pure white is left alone", FentGlass.whiteFor(0xFFFFFFFF) == 0);
      check("transparent is left alone", FentGlass.whiteFor(0x00000000) == 0);
      check("accent blue is left alone", FentGlass.whiteFor(0xFF4C9FFF) == 0);
      check("rarity gold is left alone", FentGlass.whiteFor(0xFFF5A524) == 0);
      check("danger red is left alone", FentGlass.whiteFor(0xFFFF453A) == 0);
      check("online green is left alone", FentGlass.whiteFor(0xFF4ADE80) == 0);
      check("text token grey goes white", FentGlass.whiteForToken("textSecondary", 0xFF8A8F98) == 0xFFFFFFFF);
      check("at-prefixed token works", FentGlass.whiteForToken("@textMuted", 0xFF8A8F98) == 0xFFFFFFFF);
      check("accent token is left alone", FentGlass.whiteForToken("accent", 0xFF8A8F98) == 0);
      check("textOnAccent is left alone", FentGlass.whiteForToken("textOnAccent", 0xFF8A8F98) == 0);
      check("null token is left alone", FentGlass.whiteForToken(null, 0xFF8A8F98) == 0);
      f grey = node("text", 120.0f, 10.0f, "color", "#8A8F98FF");
      check("a grey label is whitened", FentGlass.whiten(grey) != null && "#FFFFFFFF".equals(grey.b.get("color")));
      FentGlass.restore(grey, new String[] {"#8A8F98FF"}, FentGlass.textKeys());
      check("the label colour is put back", "#8A8F98FF".equals(grey.b.get("color")));
      f blue = node("text", 120.0f, 10.0f, "color", "#4C9FFFFF");
      check("a blue label is left alone", FentGlass.whiten(blue) == null && "#4C9FFFFF".equals(blue.b.get("color")));
      f plain = node("panel", 200.0f, 40.0f, "bg", "#FF1A1A1A", "rounded", "4");
      check("panels are not whitened", FentGlass.whiten(plain) == null);
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

    private static int alphaIn(String hex) {
      checkValue(hex);
      if (hex == null || hex.length() != 9 || hex.charAt(0) != '#') {
         return -1;
      }
      try {
         return Integer.parseInt(hex.substring(7, 9), 16);
      } catch (NumberFormatException exception) {
         failures.add("a colour is not #RRGGBBAA: " + hex);
         return -1;
      }
   }

   private static int rgbIn(String hex) {
      checkValue(hex);
      if (hex == null || hex.length() != 9 || hex.charAt(0) != '#') {
         return 0;
      }
      try {
         return (int)Long.parseLong(hex.substring(1, 7), 16);
      } catch (NumberFormatException exception) {
         failures.add("a colour is not #RRGGBBAA: " + hex);
         return 0;
      }
   }

   private static void checkValue(String hex) {
      if (hex == null || hex.length() != 9 || hex.charAt(0) != '#') {
         failures.add("a colour is not #RRGGBBAA: " + hex);
      }
   }

   private MaterialCheck() {
   }
}
