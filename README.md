# Spoty Widget

Client-side Fabric mod by **Kartonek** that shows the song you are currently playing — album art,
title, artist, elapsed/total time and a small animated equaliser — in the corner of the screen
(top left by default).

**No Spotify account, API key or login.** The mod reads the "now playing" information the operating
system already has from the Spotify desktop app:

- **Windows** — the media session behind the volume flyout (SMTC), read through a bundled
  PowerShell helper.
- **Linux** — the MPRIS player on D-Bus (`org.mpris.MediaPlayer2.spotify`), read through `gdbus`,
  with `playerctl` as a fallback.

macOS is not supported.

Built for **Minecraft 1.21.1 through 26.2** from one codebase using
[Stonecutter](https://stonecutter.kikugie.dev/).

## Supported versions

| Jar | Covers |
|---|---|
| `spotifywidget-1.0.0+1.21.1.jar` | 1.21, 1.21.1 |
| `spotifywidget-1.0.0+1.21.3.jar` | 1.21.2, 1.21.3 |
| `spotifywidget-1.0.0+1.21.4.jar` | 1.21.4 |
| `spotifywidget-1.0.0+1.21.5.jar` | 1.21.5 |
| `spotifywidget-1.0.0+1.21.8.jar` | 1.21.6, 1.21.7, 1.21.8 |
| `spotifywidget-1.0.0+1.21.10.jar` | 1.21.9, 1.21.10 |
| `spotifywidget-1.0.0+1.21.11.jar` | 1.21.11 |
| `spotifywidget-1.0.0+26.1.2.jar` | 26.1, 26.1.1, 26.1.2 |
| `spotifywidget-1.0.0+26.2.jar` | 26.2 |

Requires Fabric API. Mod Menu is optional but is where the settings screen lives.

## Install

Drop the jar for your game version into `mods/`, start Spotify, play something. That is the whole
setup — the widget appears in the top left corner.

## Album art

Windows does not hand out usable cover art, so the mod looks the cover up by artist and title on
Deezer, falling back to the iTunes search endpoint. Both are public and need no key. On Linux
Spotify publishes the real cover URL through MPRIS, and that is used directly.

## Settings

Mod Menu → Spoty Widget, or edit `config/spotifywidget.json`.

| Setting | Default | Notes |
|---|---|---|
| Widget | ON | Master switch |
| Design | Classic | `Classic`, `Progress bar`, `Compact`, `Above hotbar`, `Vanilla tooltip` |
| Display | Always on | `Always on`, `15 seconds`, `5 seconds` |
| Show at song start | ON | Timed modes: pop up when a track starts |
| Show at song end | ON | Timed modes: pop up for the last seconds of a track |
| Corner | Top left | Any of the four corners |
| Scale | 1.00x | 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x |
| Offset X / Y | 6 / 6 | Distance from the corner |
| Background | 88% | Panel opacity |
| Album art | ON | |
| Timestamps | ON | `00:15 / 03:45` line |
| Equaliser bars | ON | Animates while playing |
| Hide while a menu is open | OFF | |
| Jukebox in widget | ON | Show a playing record instead of the desktop player |
| Pause for jukebox | ON | Pause Spotify while a record plays, resume when it ends |
| Player | Spotify only | Switch to "Any player" to also read browsers, VLC, etc. |
| Refresh | 1000 ms | How often the player is polled |

### Designs

- **Classic** — 32px album art, title, artist, `00:15 / 03:45` and the animated equaliser.
- **Progress bar** — same art and two lines of text, elapsed/total moved to the right of the artist
  line, and a rounded green progress bar across the bottom of the panel.
- **Compact** — one slim 22px row: 16px art, then `Title · Artist`, remaining time (`-1:23`)
  right-aligned, and a 2px progress line along the bottom edge. The title dims while paused.
  Pairs well with the 0.5x scale.
- **Above hotbar** — no panel at all, just a centred line of text one line above where vanilla
  prints held item names (which is itself above the experience bar), so the two never overlap: `Title · Artist   00:15 / 03:45`, with the
  equaliser to its left when enabled. Corner is ignored for this one; Offset Y lifts the line
  higher up the screen.
- **Vanilla tooltip** — looks like an item tooltip: the same `0xF0100010` box, purple gradient
  border and white/grey/dark-grey text colours the game uses, with the album art where an item
  icon would sit. Background opacity is ignored so it matches vanilla exactly.

With **Always on** the widget is visible whenever something is playing. With **15 seconds** or
**5 seconds** it appears for that long at the start of a track and again at the end, depending on
the two "show at" toggles.

## Playback controls

Bound under **Options → Controls → Spoty Widget**, and they drive whatever the system media
session is playing - no account needed:

| Action | Default key |
|---|---|
| Next track | Right arrow |
| Previous track | Left arrow |
| Volume up | Up arrow (repeats while held) |
| Volume down | Down arrow (repeats while held) |
| Play / pause | unbound |

On Windows the transport commands go through the media session (so they hit Spotify even when the
game has focus) and volume uses the media volume keys. On Linux `playerctl` is used when present,
otherwise MPRIS over `gdbus` with `wpctl`/`pactl` for volume.

## Jukebox

When a jukebox within 64 blocks starts a record, and the Jukebox/Records sound slider is not
muted, the widget switches to the disc: song name, `Jukebox` as the artist, a spinning-record
graphic in place of album art and the time left. Spotify is paused while the record plays and
resumes on its own once it finishes (or you walk out of range). Both halves can be turned off
separately in the settings.

## Building

```bash
./gradlew buildAndCollect
```

Jars land in `build/libs/1.0.0/`. Single version: `./gradlew ":1.21.8:build"`.
To work on the code in an IDE, pick the active version with the `stonecutter` Gradle tasks
("Set active version to ...").

## How it works

- `media/WindowsMediaSource` starts `powershell.exe -EncodedCommand` with the bundled
  `assets/spotifywidget/nowplaying.ps1`, which prints one JSON line per poll and is restarted with
  backoff if it ever dies. Encoding the script sidesteps the execution policy; nothing is written
  to disk.
- `media/LinuxMediaSource` calls `gdbus`/`playerctl` on the same interval.
- Playback position is interpolated between reports, so the timer stays smooth even though Windows
  only refreshes the timeline every few seconds.
- Album art is downloaded, scaled and corner-rounded off-thread; the texture upload happens on the
  client tick, never inside HUD rendering.
- Everything that Mojang renamed between 1.21.1 and 26.2 lives in `compat/`.
- The mod is client only and never talks to the server you are playing on.
