# Fent

Client side unlock mod for SilentClient on Minecraft 1.21.11.

Fent unlocks content locally so you can use it in game. Nothing is granted on the
server side, only your own client is told that you own everything.

## Features

- Cosmetics: every cape, wing, hat, bandana, shield, icon, pet and aura is unlocked
- One toggle per cosmetic type, so you can leave pets or auras alone if you want
- Emotes: the whole emote catalog is usable, including the wheel
- Themes: custom theme and its colour options
- Premium: the service itself reports the top tier, so every premium check agrees
- Nametag message and bandana colour save locally, so the premium editors work
- Keep Coins Off stops the premium active minute reports, on by default
- Selections are saved, so your picks come back after a restart
- Aero: a Liquid Glass theme built from the Custom Theme Base and Accent colours. While it is
  the active theme the menu windows and dialogs are frosted - translucent fill, a lit top edge
  and a soft shadow under them - and the in-game HUD widgets get the same lit edge and rim over
  their own background, the Dynamic Widget island included
- Glass is only on panes: windows, dialogs and the few large plates. Cards, rows, wells and chips
  stay flat, which is what makes the panes read as glass and keeps a screen full of cards cheap
- Dynamic Widget: Date, Day, Biome and Players slots beside the shipped ones, the Version
  slot the widget could already draw, and a Lyrics Offset slider for synced lyrics
- Lyrics are kept on disk, so a track heard before comes back with its words at once

## Usage

Open the modules menu with Right Shift, then go to Settings and pick Unlocker.
Every unlock has its own toggle there, the groups, one per cosmetic type, plus
Aero Glass UI, Aero Glass HUD, Cache Lyrics and Keep Coins Off, and they are saved
with your config. Aero Glass HUD is the one to turn off if the extra draws on the
HUD ever cost you frames; the menus keep their glass either way.

Settings, then Themes lists Aero with the rest of the themes. Its colours are the
Custom Theme Base and Custom Theme Accent rows on that same page, under Settings, so
picking different colours there repaints every menu, and the glass pass follows.

## Install

Drop the jar into the launcher mods folder:

```
%APPDATA%\silentclient-launcher\game\silentclient\fabric-mods\1.21.11
```

Remove any older Fent jar first, two copies of the same mod id will stop the launch.

## Build

Needs a JDK 21 and an installed SilentClient game folder. The script finds the
client runtime jar, compiles the sources, checks every mixin target and packs the jar:

```
powershell -File build.ps1
powershell -File build.ps1 -Install
```

`-Install` copies the new jar into the launcher mods folder, and into
`.minecraft\mods\1.21.11` when your setup keeps a copy there, deleting older builds
first. `-Wait` keeps retrying while the game is open, so it finishes after you close it:

```
powershell -File build.ps1 -Install -Wait
```

## Notes

- Client side only, other players still see your real cosmetics
- Other players still see the real nametag and bandana, they are local to your client
- Coins stay on the server, the Keep Coins Off row stops the client reporting them
- Toggles live in `config.json` with the picks, the nametag and bandana live in `silentclient/fent.properties`
- Cached lyrics live in `silentclient/fent-lyrics`, one file per track, 300 at most
- The HUD material is an overlay: the widget keeps the background colour and alpha you set on it,
  and the glass adds the lit edge. The world behind a widget is not blurred, because the client
  has no blur it can apply to a widget sized area, only to the whole menu backdrop
- The log gets one line at start with the Fent version and the toggle states

## Credits

Created by Zouhmi. Website: [zouhmi.com](https://zouhmi.com)

Released under the Unlicense, see [LICENSE](LICENSE).
