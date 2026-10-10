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

// The lyrics the widget draws come from lrclib, one request per track, and they are only ever held
// in memory, so every launch fetches the same tracks again. The response for a track is kept here
// instead and served back the next time the same track comes up, which is the same JSON the client
// would have received, so its own parser and its own timings are used untouched.
public final class FentLyrics {
   private static final Path DIR = Paths.get("silentclient", "fent-lyrics");
   private static final String EXACT = "/api/get";
   private static final String SYNCED = "\"syncedLyrics\"";
   private static final String NO_SYNCED = "\"syncedLyrics\":null";
   private static final String EMPTY_SYNCED = "\"syncedLyrics\":\"\"";
   private static final int LIMIT = 300;
   private static final int PRUNE_EVERY = 32;
   private static int writes;

   // The response for the track in this request, or null when it has never been fetched.
   public static String read(String url) {
      if (!enabled() || url == null || !url.contains(EXACT)) {
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

   // Keeps a response that actually carries synced lyrics. Anything else is left uncached, so a
   // track that gains lyrics later still gets a fresh look.
   public static void write(String url, String body) {
      if (!enabled() || url == null || !url.contains(EXACT) || body == null) {
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
         Files.createDirectories(DIR);
         Files.write(file, body.getBytes(StandardCharsets.UTF_8));
         if (++writes % PRUNE_EVERY == 0) {
            prune();
         }
      } catch (IOException | RuntimeException exception) {
         System.err.println("[Fent] could not write " + file + ": " + exception);
      }
   }

   private static boolean enabled() {
      return FentUnlocks.lyricsCache();
   }

   // The duration is dropped from the key so the same track still hits after a remaster or a
   // slightly different reading of its length, and the name carries the track so the folder can be
   // read by eye.
   private static Path file(String url) {
      String normalized = url.replaceAll("[&?]duration=[0-9.]+", "");
      String name = slug(parameter(url, "artist_name")) + "-" + slug(parameter(url, "track_name"))
         + "-" + Integer.toHexString(normalized.hashCode());
      return DIR.resolve(name + ".json");
   }

   private static String parameter(String url, String key) {
      int start = url.indexOf(key + "=");
      if (start < 0) {
         return "";
      }
      start += key.length() + 1;
      int end = url.indexOf('&', start);
      return end < 0 ? url.substring(start) : url.substring(start, end);
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

   // A folder of tracks is worth keeping, an unbounded one is not: past the limit the oldest
   // answers go, which is also the order they would be wanted in.
   private static void prune() {
      List<Path> files = new ArrayList<Path>();
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(DIR, "*.json")) {
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
