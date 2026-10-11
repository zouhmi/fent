# Fent

Unlocker and custom features for SilentClient, client side, on Minecraft 1.21.11.
Made by [Zouhmi](https://zouhmi.com).

Nothing is granted on the server, only your own client is told you own everything.

## Unlocker

Modules (Right Shift) > Settings > Unlocker. Every row is a toggle, saved with your config.

| Row | What it does |
| --- | --- |
| Unlock Cosmetics | Capes, wings, hats, bandanas, shields, icons, pets, auras |
| Unlock Emotes | The whole emote catalog, wheel included |
| Unlock Themes | The custom theme and its colour rows |
| Unlock Premium | The top tier |
| Keep Coins Off | Stops the premium active minute reports |
| Cache Lyrics | Cached tracks come back with their words |
| Aero Glass UI / HUD / Refraction | The glass below, one switch per part |
| Transparent Setting Rows | Rows sit straight on the menu backdrop |
| Hide Colour Picker | Colour swatches keep their colour, no editor opens |

## Custom features

- **Aero**, a Liquid Glass theme built from the Custom Theme Base and Accent colours. Windows and
  dialogs are translucent sheets over the blurred menu backdrop, and every other plate keeps its
  own colour and hover state, only gaining a gradient.
- **The theme reaches every module**, including Keystrokes and Mouse Strokes, which paint
  themselves instead of using the shared HUD painter.
- **Real refraction**: the frame behind a widget is sampled through the framebuffer, blurred and
  bent, and drawn under the widget's softened plate. Budgeted, so a screen full of widgets stays
  cheap.
- **Dynamic Widget extras**: Date, Day, Biome and Players slots, plus a Lyrics Offset slider.
- **Account heads**: the account changer loads real heads instead of the bundled one.
- **Bare rows**: settings rows answer with the client's transparent value, no plate under each line.

## Install

Drop the jar into `%APPDATA%\silentclient-launcher\game\silentclient\fabric-mods\1.21.11` and delete
any older Fent jar first.

## Build

Needs a JDK 21 and an installed SilentClient game folder.

```
powershell -File build.ps1              # compiles, checks the mixins and the material, packs the jar
powershell -File build.ps1 -Install     # the same, then swaps the jar into the launcher
powershell -File build.ps1 -Install -Wait   # keeps retrying while the game is open
```

## Notes

- Other players still see your real cosmetics and nametag, those are local to your client
- Toggles live in `config.json`, the nametag and bandana in `silentclient/fent.properties`
- Cached lyrics live in `silentclient/fent-lyrics`, one file per track, 300 at most

Released under the Unlicense, see [LICENSE](LICENSE).
