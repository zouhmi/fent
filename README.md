# Fent

Community addon for SilentClient (client side, 1.21.11) by [Zouhmi](https://zouhmi.com).

## Features

- **Liquid Glass theme**: frosted windows and widgets with GPU backdrop blur, white menu text.
- **Glass HUD**: Keystrokes, Mouse Strokes and Dynamic Widget island, plus a glass music player.
- **Dynamic Widget extras**: Date, Day, Biome, Players, Track, Artist, Song Time, Current Lyric, Lyrics Offset.
- **Unlocks**: cosmetics, emotes, themes, Premium (local only).
- **Fent-to-Fent sharing**: share cosmetic appearances without sending ownership.
- **Small fixes**: real account heads, transparent rows, hidden colour picker, lyric cache.

## Settings

Right Shift > Settings > Fent. Toggles save to your config. Cosmetics, capes, wings,
hats, bandanas, shields, icons, pets, auras, emotes, themes, Premium, glass UI/HUD/
refraction, transparent rows, hidden picker, lyric cache, coins off, Fent-to-Fent sharing.

Sharing: picks snapshot to `silentclient/fent-share/<you>.json` on equip. Send a friend
your `FENT1-...` code or the json file; they import it and see your look. Nothing is
sent to Silent as ownership.

## Install

Drop the jar into `%APPDATA%\silentclient-launcher\game\silentclient\fabric-mods\1.21.11`,
delete older Fent jars first. Or `powershell -File build.ps1 -Install` (needs JDK 21).

Released under the Unlicense, see [LICENSE](LICENSE).
