# Automatiq — 60 sek. promo

**`out/automatiq-promo-60s.mp4`** · 1080×1920 (9:16) · 60 fps · H.264 + AAC · præcis 60,0 s · 80 BPM

![Storyboard: 12 nedslag fra filmen](out/storyboard.jpg)

En ung, beat-klippet præsentation af appen til Reels/TikTok/Shorts. Hvert klip, hver tekst-slam og
hver lydeffekt sidder på et 80 BPM-grid: 1 beat = 0,75 s, 1 takt = 3 s, 20 takter = 60 s.
Billede og lyd læser **samme tidslinje** (`timeline.js`), så en overgang og den whoosh, der sælger
den, kan ikke glide fra hinanden.

Alt er genereret fra kode: UI'et er genskabt fra appens Compose-kilder (leaf-cut-kort med åndende
åre, grøn/amber ThemedSwitch, mono-wordmark med puls-prik, blob-FAB, den kornede blå-violette
baggrund), og musik + lydeffekter er syntetiseret fra bunden (ingen samples, ingen licenser).

## Storyboard

| Tid | Beats | Scene | Tekst | Overgang ind / effekt | Lyd |
|---|---|---|---|---|---|
| 0:00 | 0–4 | Hook: samme SMS hober sig op | *Samme SMS. Samme tid. Hver. Eneste. Dag.* | bobler på 4-, 8- og 16-dele | pops der stiger i tone |
| 0:03 | 4–8 | Glitch-freeze, implosion til puls-prikken | *Hvad hvis den sendte sig selv?* | glitch + RGB-split, sug | glitch, riser, hjerteslag, stilhed |
| 0:06 | 8–12 | **Drop A**: logo-slam | *automatiq — SMS på autopilot.* | hvidt flash, shockwave, partikler | impact + 808 |
| 0:09 | 12–16 | Appen: makrolisten, kort nr. 4 tændes | *Byg en makro. Den klarer resten.* | zoom gennem robottens pupil → app-baggrund | zoom-whoosh, ticks, switch |
| 0:12 | 16–24 | Editoren: navn, modtagere, besked, `{modtager}` | *Giv den et navn. Vælg modtagere. Skriv beskeden. Personlig til hver modtager.* | FAB-blobben vokser til fuld skærm | typing, decode, dropdown-ticks |
| 0:18 | 24–40 | *Den sender, når…* + de 8 triggere (2 beats hver) | *…klokken slår / …du trykker én gang / …nogen skriver til dig / …du forlader kontoret / …du misser et opkald / …du kobler til / Otte triggere. Nul stress.* | dyk ind i ikon, whip-pan, push, zoom-through, kamerastød, strobe | ding, tap, send, sonar, vibration, plug/BT/Wi-Fi |
| 0:29 | 39–40 | Tape-stop + CRT-sluk | | billedet klemmes til en streg og en prik | tape-stop, CRT-zap |
| 0:30 | 40–48 | AI-break | *Og så… AI. · AI svarer for dig. · Du har det sidste ord. · Ny tekst hver gang.* | gnist, flip, fan-out, sug mod linsen | sparkle, shimmer, notification, riser |
| 0:36 | 48–64 | **Drop B**: 8 features à 2 beats | *Leveret. · Stille timer. · Widgets. · Mapper. · Health-tjek. · Overlever genstart. · Smarte variabler. · Backup.* | whip, slices, zoom-through, glitch, iris, push, terning | checks, chime, power down/up, decode |
| 0:48 | 64–72 | Tillid (musikken bag en væg) | *Alt kører på din telefon. Ingen konto. Ingen server. AI kun, hvis du vil.* | telefonen spinner og dykker | thud, switch, riser |
| 0:54 | 72–76 | Recap på hvert beat | *Planlæg. Svar. Automatisér. Slap af.* | farveblokke klippet på beatet | fire hits |
| 0:57 | 76–80 | End card | *automatiq · SMS på autopilot. · larssohl.dk* | impact, ringe | sidste akkord + hjerteslag |

Musikken: trap i C-mol, 80 BPM (Cm9 · Ab · Eb · Bb), 808 med glides, clap på 2 og 4, rullende
hi-hats, pad, FM-pluck-hook og lead i drop B. Der er kick på 1 og 3 i alle groove-takter, fordi
billedet klipper dér. Stille "gaps" på ½ beat før hvert drop. 41 forskellige syntetiserede
lydeffekter fordelt på 119 cues; musikken duckes automatisk under effekterne (sidechain), så
overgangene altid står igennem. Mastereret til −14 LUFS integreret (true peak −1,2 dBTP før AAC).

## Struktur

```
promo/
  timeline.js            fælles tidslinje: 28 shots, sektioner, trommegrid, gaps, 119 SFX-cues (beats)
  scene/                 deterministisk HTML-renderer: window.renderFrame(t)
    index.html, style.css
    lib.js               easing, springs, seeded noise, kinetisk typografi
    ui.js                appens UI genskabt (MacroCard, ThemedSwitch, telefon, widget …)
    shots*.js            de 28 shots, ét pr. tidslinje-indgang
    main.js              shot-scheduler, kamera (kick-puls, rystelse), flash, glitch, grain
  audio/
    dsp.py               oscillatorer (polyBLEP), filtre, reverb, delay, limiter
    soundtrack.py        arrangement + SFX-bibliotek + mix/master → WAV
  render.cjs             Playwright → frames → ffmpeg (x264/AAC) med lyd
  assets/                DM Sans + JetBrains Mono (OFL), robot-ikonet i 3× størrelse
  out/                   færdig video
```

## Render igen

Krav: Node 18+, Playwright med Chromium (`npm install && npx playwright install chromium` i `promo/`),
Python 3 med `numpy` + `scipy`, og `ffmpeg` med libx264.

```bash
cd promo
node render.cjs                         # fuld render → out/automatiq-promo-60s.mp4 (10–15 min på 4 kerner)
node render.cjs --sheet 18:30:0.375     # kontaktark af et udsnit → build/sheet.png
node render.cjs --preview 6,9.5,36      # enkelte stills → build/preview/
```

Flag: `--fps 60`, `--crf 22` (x264-kvalitet; 22 giver ~30 MB), `--workers 4`, `--from/--to` (sekunder),
`--no-audio`, `--keep-frames`, `--out navn.mp4`.

Ret tekster i `scene/shots*.js`, timing og lydeffekter i `timeline.js`, musikken i
`audio/soundtrack.py`.

## Licenser

- DM Sans og JetBrains Mono: SIL Open Font License 1.1 (`assets/fonts/OFL-*.txt`).
- Ikoner: Material Icons (Google), Apache License 2.0.
- Robot-ikonet og baggrundsbilledet kommer fra appens egne ressourcer.
- Musik og lydeffekter er syntetiseret i `audio/` og må bruges frit sammen med videoen.
