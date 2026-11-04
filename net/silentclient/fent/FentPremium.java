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

public final class FentPremium {
   private static volatile Path file;
   private static Properties store;
   private static NametagConfig nametag;
   private static BandanaConfig bandana;
   private static boolean loaded;

   private static Path file() {
      Path resolved = file;
      if (resolved == null) {
         synchronized (FentPremium.class) {
            resolved = file;
            if (resolved == null) {
               try {
                  resolved = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                     .resolve("silentclient").resolve("fent.properties");
               } catch (Throwable throwable) {
                  resolved = Paths.get("silentclient", "fent.properties");
               }
               file = resolved;
            }
         }
      }
      return resolved;
   }

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
      if (config == null) {
         return;
      }
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
      if (config == null) {
         return;
      }
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
      Path path = file();
      if (Files.isRegularFile(path)) {
         try (InputStream input = Files.newInputStream(path)) {
            store.load(input);
         } catch (IOException exception) {
            System.err.println("[Fent] could not read " + path + ": " + exception);
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
      Path path = file();
      try {
         Path parent = path.getParent();
         if (parent != null) {
            Files.createDirectories(parent);
         }
         Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
         try (OutputStream output = Files.newOutputStream(tmp)) {
            store.store(output, "Fent");
         }
         try {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
            Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         }
      } catch (IOException exception) {
         System.err.println("[Fent] could not write " + path + ": " + exception);
      }
   }

   private static String text(String key) {
      String value = store.getProperty(key);
      return value == null ? "" : value;
   }

   private static int number(String key, int fallback) {
      String raw = store.getProperty(key);
      if (raw == null) {
         return fallback;
      }
      try {
         return Integer.parseInt(raw.trim());
      } catch (NumberFormatException exception) {
         return fallback;
      }
   }

   private FentPremium() {
   }
}
