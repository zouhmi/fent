package net.silentclient.fent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.silentclient.common.api.services.cosmetics.CosmeticType;

/* APPLIED + FIELDS are P1 hardening: stops only fire for emotes this client
 * actually applied (never wiping a peer's native emote), and record field
 * lookups are cached instead of reflected per peer per apply cycle. */

/**
 * Bridges FentShare's remote state into SilentClient's own per-player state
 * manager (net.silentclient.internal.aq.F) so shared cosmetics and emotes
 * render through SilentClient's native code path - no custom renderers.
 *
 * IMPORTANT: SilentClient creates and re-creates its own record for a player
 * (keyed by its own ids and indexed by lowercased name). Instead of fighting
 * over the name index, this class looks up the record SilentClient already
 * has for a peer and writes fent state INTO that record, then re-asserts
 * (cheap equal-check every apply cycle) whenever SilentClient replaces it.
 * Players without a native record still get a stable synthetic record.
 */
public final class FentInterop {
   /** High bit prefix keeps synthetic keys far away from real SilentClient ids. */
   private static final long KEY_PREFIX = 0x4600000000000000L;

   /** The 7 render slots SilentClient's aq.k applier recognizes (plural form). */
   private static final String[] SLOTS = {
      "capes", "wings", "bandanas", "hats", "shields", "pets", "auras"
   };

   /** Peers we applied a non-empty emote to and haven't stopped since. */
   private static final java.util.Set<String> APPLIED =
      java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
   /** Record field cache: F$c field lookups happen per peer per apply cycle. */
   private static final ConcurrentHashMap<String, java.lang.reflect.Field> FIELDS =
      new ConcurrentHashMap<String, java.lang.reflect.Field>();

   private static java.lang.reflect.Field fent$field(Class<?> type, String name) {
      String key = type.getName() + "#" + name;
      java.lang.reflect.Field found = FIELDS.get(key);
      if (found == null) {
         try {
            found = type.getField(name);
            FIELDS.put(key, found);
         } catch (Throwable ignored) {
            return null;
         }
      }
      return found;
   }

   private static final FentInterop INSTANCE = new FentInterop();

   private FentInterop() {
   }

   public static FentInterop get() {
      return INSTANCE;
   }

   public static long keyFor(String name) {
      String lower = name.toLowerCase(Locale.ROOT);
      long hash = 0L;
      for (int i = 0; i < lower.length(); ++i) {
         hash = hash * 1099511628211L + (long) lower.charAt(i);
      }
      long key = KEY_PREFIX | (hash & 0x0000FFFFFFFFFFFFL);
      return key <= 0L ? KEY_PREFIX + 1L : key;
   }

   /**
    * Pushes a peer's cosmetics and (optional) emote state into the player
    * registry. items null means "leave cosmetics untouched"; a non-null map
    * REPLACES the record's cosmetics for fent's 7 render slots (missing slot
    * = cleared), so fent cosmetics take priority over SilentClient's when a
    * fent row exists; when the fent row is empty/absent native cosmetics
    * stay visible.
    */
   public static void applyPlayer(String name, Map<CosmeticType, String> items,
         String emoteId, long emoteStartedAt, boolean emoteInfinite) {
      if (name == null || name.isEmpty()) {
         return;
      }
      try {
         String own = FentShare.syncName();
         if (own != null && !own.isEmpty() && own.equalsIgnoreCase(name)) {
            return; // our own cosmetics are managed by SilentClient itself
         }
         Map<String, String> selected;
         if (items != null) {
            selected = new HashMap<String, String>();
            for (Map.Entry<CosmeticType, String> entry : items.entrySet()) {
               String key = nativeKey(entry.getKey());
               if (key != null && entry.getValue() != null && !entry.getValue().isEmpty()) {
                  selected.put(key, entry.getValue());
               }
            }
         } else {
            selected = null;
         }
         String emote = emoteId == null ? "" : emoteId;
         if (!emote.isEmpty()) {
            APPLIED.add(name.toLowerCase(Locale.ROOT));
         }

         net.silentclient.internal.aq.F f = net.silentclient.internal.aq.F.a();
         Object record = null;
         try {
            // F.a(String) is a UUID lookup (it matches the stripped name against
            // record field "c", the uuid) - calling it with a display name always
            // missed, so we kept creating a synthetic record that overwrote
            // SilentClient's own entry in the name index. F.b(String) is the
            // real name-indexed accessor (map "e", lowercased fallback).
            record = f.b(name);
         } catch (Throwable ignored) {
         }
         if (record == null) {
            try {
               record = f.a(name); // last resort: uuid-shaped identifiers
            } catch (Throwable ignored) {
            }
         }

         long key = keyFor(name);
         String displayName = name;
         if (record != null) {
            // write into the record SilentClient itself owns for this player
            Long realKey = getLong(record, "a");
            String recName = getString(record, "b");
            if (realKey != null && realKey.longValue() > 0L) {
               key = realKey.longValue();
            }
            if (recName != null && !recName.isEmpty()) {
               displayName = recName;
            }
            if (matchesRecord(record, selected, emote, emoteStartedAt, emoteInfinite)) {
               return; // already showing this exact state - skip the churn
            }
         }

         f.a(key, displayName, (String) null, emote,
            emoteStartedAt > 0L ? emoteStartedAt : 0L, selected, true, emoteInfinite, "", "");
      } catch (Throwable ignored) {
         // interop must never break the caller
      }
   }

   /**
    * True when the record already shows exactly the requested cosmetics
    * (our 7 slots) and emote state; used to avoid re-writing every cycle
    * while STILL re-asserting after SilentClient recreates the record.
    */
   private static boolean matchesRecord(Object record, Map<String, String> selected,
         String emote, long emoteStartedAt, boolean emoteInfinite) {
      try {
         if (selected != null) {
            java.lang.reflect.Field eField = fent$field(record.getClass(), "e");
         Object eObj = eField == null ? null : eField.get(record);
            Map current = eObj instanceof Map ? (Map) eObj : null;
            for (String slot : SLOTS) {
               String want = selected.get(slot);
               String have = current == null ? null : (String) current.get(slot);
               if (want == null) {
                  if (have != null && !have.isEmpty()) {
                     return false;
                  }
               } else if (!want.equals(have)) {
                  return false;
               }
            }
         }
         String curEmote = getString(record, "f");
         if (!emote.equals(curEmote == null ? "" : curEmote)) {
            return false;
         }
         if (emoteStartedAt > 0L) {
            Long haveAt = getLong(record, "i");
            if (haveAt != null && haveAt.longValue() != emoteStartedAt) {
               return false;
            }
         }
         return true;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private static String getString(Object record, String field) {
      try {
         java.lang.reflect.Field found = fent$field(record.getClass(), field);
         if (found == null) {
            return null;
         }
         Object value = found.get(record);
         return value instanceof String ? (String) value : null;
      } catch (Throwable ignored) {
         return null;
      }
   }

   private static Long getLong(Object record, String field) {
      try {
         java.lang.reflect.Field found = fent$field(record.getClass(), field);
         if (found == null) {
            return null;
         }
         Object value = found.get(record);
         return value instanceof Long ? (Long) value : null;
      } catch (Throwable ignored) {
         return null;
      }
   }

   /**
    * Clears a peer's running emote without touching their cosmetics. Only
    * fires for emotes this client applied: cosmetics-only peers (and peers
    * emoting through SilentClient itself) are never touched.
    */
   public static void stopEmote(String name) {
      if (name == null || name.isEmpty()) {
         return;
      }
      // every per-second applyRemoteFor for a cosmetics-only peer used to end
      // up here doing a registry lookup + engine call; now it returns here.
      if (!APPLIED.remove(name.toLowerCase(Locale.ROOT))) {
         return;
      }
      try {
         String own = FentShare.syncName();
         if (own != null && !own.isEmpty() && own.equalsIgnoreCase(name)) {
            return;
         }
         net.silentclient.internal.aq.F f = net.silentclient.internal.aq.F.a();
         Object record = null;
         try {
            record = f.b(name); // name index (F.a(String) is the uuid lookup)
         } catch (Throwable ignored) {
         }
         if (record == null) {
            try {
               record = f.a(name);
            } catch (Throwable ignored) {
            }
         }
         long key = keyFor(name);
         String uuid = null;
         if (record != null) {
            Long realKey = getLong(record, "a");
            if (realKey != null && realKey.longValue() > 0L) {
               key = realKey.longValue();
            }
            uuid = getString(record, "c");
         }
         f.b(key);
         if (uuid != null && !uuid.isEmpty()) {
            net.silentclient.internal.as.f.a().a(uuid);
         }
      } catch (Throwable ignored) {
      }
   }

   /** SilentClient's own aq.k table maps these plural keys to render slots. */
   private static String nativeKey(CosmeticType type) {
      if (type == CosmeticType.CAPE) {
         return "capes";
      } else if (type == CosmeticType.WINGS) {
         return "wings";
      } else if (type == CosmeticType.BANDANA) {
         return "bandanas";
      } else if (type == CosmeticType.HAT) {
         return "hats";
      } else if (type == CosmeticType.SHIELD) {
         return "shields";
      } else if (type == CosmeticType.PET) {
         return "pets";
      } else if (type == CosmeticType.AURA) {
         return "auras";
      }
      return null; // ICON has no in-world render slot
   }
}
