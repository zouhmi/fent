# Fent

Community addon for SilentClient (client side, 1.21.11) by [Zouhmi](https://zouhmi.com).

## Features

- **Liquid Glass theme**: frosted windows and widgets with GPU backdrop blur, grey menu text.
- **Glass HUD**: Keystrokes, Mouse Strokes and Dynamic Widget island, plus a glass music player.
- **Dynamic Widget extras**: Date, Day, Biome, Players, Track, Artist, Song Time, Current Lyric, Lyrics Offset.
- **Unlocks**: cosmetics, emotes, themes, Premium (local only).
- **Fent-to-Fent sharing**: export and import local cosmetic appearances without sending ownership.
- **Small fixes**: real account heads, transparent rows, hidden colour picker, lyric cache.

## Settings

Right Shift > Settings > Fent. Toggles save to your config. Cosmetics, capes, wings,
hats, bandanas, shields, icons, pets, auras, emotes, themes, Premium, glass UI/HUD/
refraction, transparent rows, hidden picker, lyric cache, coins off, Fent-to-Fent sharing.

Sharing: run `/fentshare export` to get a `FENT1-...` code, then share it with a friend.
They can run `/fentshare import <player-uuid> <code>` to show that appearance locally.
Nothing is sent to Silent as ownership.

## Install

Drop the jar into `%APPDATA%\silentclient-launcher\game\silentclient\fabric-mods\1.21.11`,
delete older Fent jars first. Or `powershell -File build.ps1 -Install` (needs JDK 21).

Released under the Unlicense, see [LICENSE](LICENSE).
