# Fent

Community addon for SilentClient (client side, 1.21.11) by [Zouhmi](https://zouhmi.com).

## Features

- **Liquid Glass theme**: frosted windows and widgets with GPU backdrop blur, grey main-menu text and icons.
- **Glass HUD**: Keystrokes, Mouse Strokes and Dynamic Widget island, plus a glass music player.
- **Dynamic Widget extras**: Date, Day, Biome, Players, Track, Artist, Song Time, Current Lyric, Lyrics Offset.
- **Unlocks**: cosmetics, emotes, themes, Premium (local only).
- **Fent-to-Fent sharing**: export and import local cosmetic appearances without sending ownership.
- **Small fixes**: real account heads, hidden colour picker, lyric cache.

## Settings

Right Shift > Settings > Fent. Toggles save to your config. Cosmetics, capes, wings,
hats, bandanas, shields, icons, pets, auras, emotes, themes, Premium, glass UI/HUD/
refraction, hidden picker, lyric cache, coins off, Fent-to-Fent sharing.

Sharing: run `/fentshare export` to get a `FENT1-...` code, then share it with a friend.
They can run `/fentshare import <player-uuid> <code>` to show that appearance locally.
Nothing is sent to Silent as ownership.

## Sync API (homelab)

The client can sync cosmetic appearances through a small API so fent users see
each other's cosmetics in real time without owning them locally or trading
share codes. In game chat:

- `/fentshare api https://api.zouhmi.com` - point the client at your API (or `/fentshare api off`)
- `/fentshare key <key>` - set the server's Fent-Key (server config: `~/fent-api/fent_config.json`)
- `/fentshare name <name>` - the id you publish under (defaults to your name)
- `/fentshare resync` - force an immediate publish + pull

Endpoints: `GET /api/cosmetics`, `GET /api/cosmetics/<id>`, `PUT /api/cosmetics/<id>`,
`GET /api/emotes`, `GET /api/emotes/<id>`, `PUT /api/emotes/<id>`, `GET /api/events`
(SSE realtime), `GET /api/health`. All writes and all DELETEs need the Fent-Key
(the server refuses unkeyed writes when a key is configured). Server code:
`server/fent_api.py` (canonical - the `Fent-2/api/server.js` rewrite is deprecated,
see its DEPRECATED.md). Deploy: `ssh api@192.168.0.67 /home/api/fent-api/restart.sh`.

## Install

Drop the jar into `%APPDATA%\silentclient-launcher\game\silentclient\fabric-mods\1.21.11`,
delete older Fent jars first. Or `powershell -File build.ps1 -Install` (needs JDK 21).

Released under the Unlicense, see [LICENSE](LICENSE).
