package net.silentclient.fent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import net.silentclient.common.api.services.premium.BandanaConfig;
import net.silentclient.common.api.services.premium.NametagConfig;

// Local nametag and bandana store, kept next to the client's own config.json.
// The server refuses both saves without real premium, so Fent writes them here.
public final class FentPremium {
   private static final Path FILE = Paths.get("silentclient", "fent.properties");
   private static Properties store;
   private static NametagConfig nametag;
   private static BandanaConfig bandana;
   private static boolean loaded;

   public static synchronized NametagConfig nametag() {
      if (!loaded) {
         load();
      }
      return nametag;
   }

   public static synchronized BandanaConfig bandana() {
      if (!loaded) {
         load();
      }
      return bandana;
   }

   public static synchronized void saveNametag(NametagConfig config) {
      if (!loaded) {
         load();
      }
      nametag = config;
      store.setProperty("nametag.saved", "true");
      store.setProperty("nametag.text", config.getText() == null ? "" : config.getText());
      store.setProperty("nametag.color", Integer.toString(config.getColor()));
      store.setProperty("nametag.rainbow", Boolean.toString(config.isRainbow()));
      store.setProperty("nametag.visible", Boolean.toString(config.isVisible()));
      flush();
   }

   public static synchronized void saveBandana(BandanaConfig config) {
      if (!loaded) {
         load();
      }
      bandana = config;
      store.setProperty("bandana.saved", "true");
      store.setProperty("bandana.color", Integer.toString(config.getColor()));
      store.setProperty("bandana.rainbow", Boolean.toString(config.isRainbow()));
      flush();
   }

   private static void load() {
      loaded = true;
      store = new Properties();
      if (Files.isRegularFile(FILE)) {
         try (InputStream input = Files.newInputStream(FILE)) {
            store.load(input);
         } catch (IOException exception) {
            System.err.println("[Fent] could not read " + FILE + ": " + exception);
         }
      }
      if ("true".equals(store.getProperty("nametag.saved"))) {
         nametag = new NametagConfig(
            text("nametag.text"),
            number("nametag.color", -1),
            Boolean.parseBoolean(store.getProperty("nametag.rainbow")),
            Boolean.parseBoolean(store.getProperty("nametag.visible"))
         );
      }
      if ("true".equals(store.getProperty("bandana.saved"))) {
         bandana = new BandanaConfig(
            number("bandana.color", -678620),
            Boolean.parseBoolean(store.getProperty("bandana.rainbow"))
         );
      }
   }

   private static void flush() {
      try {
         Path parent = FILE.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }
         try (OutputStream output = Files.newOutputStream(FILE)) {
            store.store(output, "Fent");
         }
      } catch (IOException exception) {
         System.err.println("[Fent] could not write " + FILE + ": " + exception);
      }
   }

   private static String text(String key) {
      String value = store.getProperty(key);
      return value == null ? "" : value;
   }

   private static int number(String key, int fallback) {
      try {
         return Integer.parseInt(store.getProperty(key));
      } catch (NumberFormatException exception) {
         return fallback;
      }
   }

   private FentPremium() {
   }
}
