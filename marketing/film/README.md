# TextFox film

A 59-second, 1920×1080 motion film for TextFox, rendered from code so it can be re-cut when the app or copy changes.

| File | What it does |
|---|---|
| `film.html` | The whole film as one deterministic page: GSAP timeline + three.js 3D fox. `window.renderAt(seconds)` draws one frame. Scenes sit on the soundtrack's 4-bar phrases (bar 2.4 s, first downbeat 1.18 s). It also exports the sound-effect cues (`window.FILM.CUES`). |
| `render.js` | Renders every frame to JPEG with Playwright and writes `cues.json`. |
| `sfx.py` | Synthesises the robot sound effects (FM bleeps, servo, chirps, impacts — no samples) at the cues and mixes them over the soundtrack. |

The soundtrack (`CrazyTunes_Robotic_short-02.mp3`) is not in git.

## Build

```bash
python3 -m http.server 8766                      # from the repo root
node marketing/film/render.js /tmp/tf/frames     # ~30 min, 1786 frames
python3 marketing/film/sfx.py /tmp/tf/frames/cues.json CrazyTunes_Robotic_short-02.mp3 /tmp/tf/mix.wav
ffmpeg -framerate 30 -i /tmp/tf/frames/f%05d.jpg -i /tmp/tf/mix.wav \
  -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p -movflags +faststart \
  -af loudnorm=I=-14:TP=-1:LRA=11 -c:a aac -b:a 256k -shortest textfox-film.mp4
```

## Scenes

1. **Boot · Meet TextFox** — a green dot powers up, the 3D fox assembles, its eyes type, and it says hi.
2. **A day on autopilot** — the camera dives into the fox's eye; a clock runs 06:59 → 17:40 while the log fills.
3. **Four ways to fire a macro** — schedule, one tap, auto-reply, missed call.
4. **AI built in** — a Gemini variation rewrites itself; ask first or send automatically.
5. **Reliable and private** — fires on the minute, delivered ✓✓, runs on your phone.
6. **Finale** — green flash, the fox with its orbit of texts, Google Play and the logo.
