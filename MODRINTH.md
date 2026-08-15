# Spotify Widget

Shows the song you are currently playing — album art, title, artist, time and a small animated
equaliser — in the corner of your Minecraft screen.

**No Spotify account. No API key. No login. No client id.** Install the jar, play music, done.

The mod reads the "now playing" information your operating system already has from the Spotify
desktop app, the same data your volume flyout shows. It never touches the Spotify Web API, so
there is nothing to sign up for and nothing to configure.

---

## Five designs

Cycle them with one button in the settings.

| Design | Looks like |
|---|---|
| **Classic** | Rounded dark panel: 32px album art, title, artist, `00:15 / 03:45`, animated equaliser |
| **Progress bar** | Same panel, timestamps on the artist line, green progress bar across the bottom |
| **Compact** | One slim 22px row: `Title · Artist`, remaining time, thin progress line |
| **Above hotbar** | No panel at all — a centred line of text just above the held-item name |
| **Vanilla tooltip** | Drawn exactly like an item tooltip: dark box, purple gradient border, vanilla text colours |

Any corner, scale from **0.5x to 2x**, adjustable offsets, background opacity, and individual
toggles for album art, timestamps and the equaliser.

## Show it only when you want it

- **Always on** — visible whenever something is playing
- **15 seconds** or **5 seconds** — pops up at the start of a track and again near its end

Both triggers can be turned on or off separately, so you can have it appear only when a song
starts, only as one ends, or both.

## Playback controls

Bound under **Options → Controls → Spotify Widget**:

| Action | Default key |
|---|---|
| Next track | Right arrow |
| Previous track | Left arrow |
| Volume up | Up arrow (repeats while held) |
| Volume down | Down arrow (repeats while held) |
| Play / pause | unbound by default |

These drive the system media session directly, so they reach Spotify even while Minecraft has
focus — no alt-tabbing.

## Jukebox aware

Drop a record in a jukebox and the widget switches to it: disc name, time left, and a little vinyl
graphic in place of album art. Spotify is paused while the record plays and resumes by itself once
the disc finishes or you walk out of range. Skipped entirely if your Jukebox/Records sound slider
is muted. Both halves can be switched off.

## Album art

On Linux the real cover comes straight from the player. On Windows the cover is looked up by
artist and title on Deezer, with the iTunes search endpoint as a fallback — both public, keyless
services. Art is downloaded off-thread, scaled and corner-rounded before it ever reaches the GPU.

---

## Requirements

- **Fabric Loader** 0.17+ and **Fabric API**
- **Mod Menu** — optional, but that is where the settings screen lives
- **Windows** or **Linux** desktop (macOS is not supported)
- Client side only. It never talks to the server you are playing on, and works fine on servers
  that do not have it.

## Supported versions

| Download | Works on |
|---|---|
| `+1.21.1` | 1.21, 1.21.1 |
| `+1.21.3` | 1.21.2, 1.21.3 |
| `+1.21.4` | 1.21.4 |
| `+1.21.5` | 1.21.5 |
| `+1.21.8` | 1.21.6, 1.21.7, 1.21.8 |
| `+1.21.10` | 1.21.9, 1.21.10 |
| `+1.21.11` | 1.21.11 |
| `+26.1.2` | 26.1, 26.1.1, 26.1.2 |
| `+26.2` | 26.2 |

One codebase, built for every one of them with [Stonecutter](https://stonecutter.kikugie.dev/).

## How it reads the player

- **Windows** — a bundled PowerShell helper asks the system media session
  (`Windows.Media.Control`) what is playing and prints it back to the mod once a second. The script
  is passed in encoded, so nothing is written to disk and no execution-policy change is needed.
  You can read it in the jar at `assets/spotifywidget/nowplaying.ps1`.
- **Linux** — the MPRIS player on D-Bus, through `gdbus` (ships with glib) or `playerctl`.

Playback position is interpolated between reports, so the timer ticks smoothly instead of jumping.

## Privacy

No telemetry, no accounts, no server communication. The only outgoing requests are the album-art
lookups (artist and track title sent to Deezer/iTunes) and downloading the cover image itself.
On Linux even those are skipped, because the player provides the cover URL.

## Configuration

Everything lives in **Mod Menu → Spotify Widget**, or `config/spotifywidget.json` if you prefer a
text editor.

---

*Made by Kartonek. Licensed MIT.*
