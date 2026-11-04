package net.silentclient.fent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FentLyrics {
   private static final String EXACT = "/api/get";
   private static final String SYNCED = "\"syncedLyrics\"";
   private static final String NO_SYNCED = "\"syncedLyrics\":null";
   private static final String EMPTY_SYNCED = "\"syncedLyrics\":\"\"";
   private static final int LIMIT = 300;
   private static final int PRUNE_EVERY = 32;
   private static final java.util.regex.Pattern DURATION_PARAM =
      java.util.regex.Pattern.compile("[&?]duration=[0-9.]+");
   private static int writes;
   private static volatile Path dir;

   private static Path dir() {
      Path resolved = dir;
      if (resolved == null) {
         synchronized (FentLyrics.class) {
            resolved = dir;
            if (resolved == null) {
               try {
                  resolved = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                     .resolve("silentclient").resolve("fent-lyrics");
               } catch (Throwable throwable) {
                  resolved = Paths.get("silentclient", "fent-lyrics");
               }
               dir = resolved;
            }
         }
      }
      return resolved;
   }

   public static synchronized String read(String url) {
      if (url == null || !url.contains(EXACT) || !enabled()) {
         return null;
      }
      Path file = file(url);
      try {
         if (!Files.isRegularFile(file)) {
            return null;
         }
         String body = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
         return body.isEmpty() ? null : body;
      } catch (IOException | RuntimeException exception) {
         System.err.println("[Fent] could not read " + file + ": " + exception);
         return null;
      }
   }

   public static synchronized void write(String url, String body) {
      if (url == null || !url.contains(EXACT) || body == null || !enabled()) {
         return;
      }
      if (!body.contains(SYNCED)
         || body.contains(NO_SYNCED)
         || body.contains(EMPTY_SYNCED)
         || body.length() > 512 * 1024) {
         return;
      }
      Path file = file(url);
      try {
         Files.createDirectories(dir());
         if (Files.isRegularFile(file)) {
            try {
               String existing = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
               if (body.equals(existing)) {
                  return;
               }
            } catch (IOException | RuntimeException ignored) {
            }
         }
         Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
         Files.write(tmp, body.getBytes(StandardCharsets.UTF_8));
         try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
               java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         } catch (java.nio.file.AtomicMoveNotSupportedException atomic) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
         }
         if (++writes % PRUNE_EVERY == 0) {
            prune();
         }
      } catch (IOException | RuntimeException exception) {
         System.err.println("[Fent] could not write " + file + ": " + exception);
      }
   }

   private static boolean enabled() {
      try {
         return FentUnlocks.lyricsCache();
      } catch (Throwable throwable) {
         return false;
      }
   }

   private static Path file(String url) {
      String normalized = DURATION_PARAM.matcher(url).replaceAll("");
      String name = slug(parameter(url, "artist_name")) + "-" + slug(parameter(url, "track_name"))
         + "-" + Integer.toHexString(normalized.hashCode());
      return dir().resolve(name + ".json");
   }

   private static String parameter(String url, String key) {
      int start = url.indexOf(key + "=");
      if (start < 0) {
         return "";
      }
      start += key.length() + 1;
      int end = url.indexOf('&', start);
      String raw = end < 0 ? url.substring(start) : url.substring(start, end);
      try {
         return java.net.URLDecoder.decode(raw, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException bad) {
         return raw;
      }
   }

   private static String slug(String text) {
      if (text == null || text.isEmpty()) {
         return "x";
      }
      StringBuilder out = new StringBuilder(text.length());
      for (int index = 0; index < text.length() && out.length() < 40; ++index) {
         char character = text.charAt(index);
         boolean plain = character >= 'a' && character <= 'z'
            || character >= 'A' && character <= 'Z'
            || character >= '0' && character <= '9';
         out.append(plain || character == '_' ? character : '_');
      }
      return out.length() == 0 ? "x" : out.toString();
   }

   private static void prune() {
      List<Path> files = new ArrayList<Path>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir(), "*.json")) {
         for (Path path : stream) {
            files.add(path);
         }
      } catch (IOException exception) {
         return;
      }
      if (files.size() <= LIMIT) {
         return;
      }
      files.sort(Comparator.comparingLong(FentLyrics::modified));
      for (int index = 0; index < files.size() - LIMIT; ++index) {
         try {
            Files.deleteIfExists(files.get(index));
         } catch (IOException exception) {
            return;
         }
      }
   }

   private static long modified(Path path) {
      try {
         FileTime time = Files.getLastModifiedTime(path);
         return time.toMillis();
      } catch (IOException exception) {
         return 0L;
      }
   }

   private FentLyrics() {
   }
}
