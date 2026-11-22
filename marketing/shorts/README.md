# TextFox shorts

Lodrette klip (1080×1920, 30 fps, 15–25 s) til TikTok, Reels og YouTube Shorts, rendret fra kode, som filmene i `../film/` og `../../promo/`.
Billede, musik og lyd er genereret her (ingen samples, ingen licenser). Salgsplanen står i [`../PLAN.md`](../PLAN.md).

| Fil | Hvad |
|---|---|
| `clips.json` | Alle klip: manuskript, makro, CTA og `post` (caption, hashtags-tekst, YouTube-titel, første kommentar). Øverst: `promo` (rabatlinjen i CTA'en). |
| `clip.html` | Motoren. Deterministisk: `window.renderAt(sekunder)` tegner ét billede. `?c=<id>` vælger klip. To skabeloner. |
| `icons.html` | Ikon-symboler og ræven, hentet fra `website/build.py` (`ICONS`). Opdatér den, hvis ikonerne ændres. |
| `render.js` | Playwright: skriver hvert billede som JPEG + `cues.json` (lydeffekter) + `meta.json`. |
| `score.py` | Kort partitur (80 BPM) af filmens instrumenter. Seed skifter toneart. |
| `make.sh` | Hele kæden: billeder → musik → lydeffekter (`../film/sfx.py`) → mp4 i `out/`. |

`out/` ligger ikke i git. Klippene kan genskabes præcist.

## Rendér

```bash
bash marketing/shorts/make.sh                    # alle klip i clips.json
bash marketing/shorts/make.sh running-late       # ét klip
JOBS=2 bash marketing/shorts/make.sh             # antal samtidige (standard 3)
```

Kræver node + playwright, ffmpeg og python3 med numpy. Ca. 1,5 minut pr. klip. Nye klip sættes **sidst** i `clips.json`: pladsen i listen bestemmer musikkens toneart. Lydniveau: −14 LUFS.

## Skabeloner

**`macro`** (Steal this macro): hook → et makrokort bygges op (navn, WHEN, TO, SAY, kontakten tændes) → makroen udløses → CTA.
Feltet `fire.kind` vælger udløsningen: `widget` (tryk på widget), `clock` (klokken slår), `missed` (ubesvaret opkald), `incoming` (en sms kommer ind), `none` (bruges med `thread.bubbles`, flere dages AI-varianter).

**`fox`** (Fox says): ræven og en chat; `lines` er skiftevis `me` og `fox`. Ræven "skriver" før hver replik.

**`teaser`** (Coming soon): ræven vågner i mørket (tre prikker tændes, ræven samles), "Something clever is coming to Android", en kort liste og CTA. Felter: `kicker`, `hook`, `list`, `cta`, `promo` og `groove_from` (sekund hvor beatet starter; før det kun pads og en riser).

Nyt klip: kopiér et objekt i `clips.json`, skift `id`, tekster og farve (`rose`, `amber`, `teal`, `green`, `indigo`), og kør `make.sh <id>`. Hook-linjer med `*stjerner*` bliver grønne. Ikon: `tap`, `clock`, `reply`, `missed`, `spark`, `bell`, `moon`.

## Regler

- Skærmen viser et **genskabt** UI i appens udtryk. Mærk det aldrig som skærmoptagelse, og brug rigtige optagelser fra telefonen til tutorials og alt, der skal bevise, at noget virker.
- Tekst holdes i sikkerhedszonen (ikke de nederste ca. 450 px, ikke yderst til højre).
- Telefonnumre er fiktive (555-01xx).
- Efter Early Bird (31. dec.): sæt `"promo": null` og rendér igen.
