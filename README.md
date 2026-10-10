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

## Usage

Open the modules menu with Right Shift, then go to Settings and pick Unlocker.
Every unlock has its own toggle there, the groups, one per cosmetic type, plus
Keep Coins Off, and they are saved with your config.

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
- The log gets one line at start with the Fent version and the toggle states

## Credits

Created by Zouhmi. Website: [zouhmi.com](https://zouhmi.com)

Released under the Unlicense, see [LICENSE](LICENSE).
