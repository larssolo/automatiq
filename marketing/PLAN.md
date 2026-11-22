# TextFox: salgsplan (budget 0 kr)

Sidst opdateret 22. november 2026 (Windsor-handlinger verificeret med `list_actions`). Ejer: Lars. Udfører: Claude (klip, tekster, hjemmeside, tal) + Lars til det, kun han kan (upload, Play Console, Reddit).
Alt indhold, der postes, er **på engelsk**. Selve planen er på dansk, ligesom resten af interne dokumenter.

> Læs afsnit 1 først. Resten er værktøjskassen: hvad der skal tjekkes, hvem der gør hvad, hvad der postes hvornår, og hvordan vi ved, om det virker.

## Indhold

1. [Ærligt udgangspunkt](#1-ærligt-udgangspunkt)
2. [Beslutninger fra Lars](#2-beslutninger-fra-lars-22-nov)
3. [Status: hvad er gjort](#3-status-hvad-er-gjort)
4. [Porte: tjek før vi sender trafik](#4-porte-tjek-før-vi-sender-trafik)
5. [Adgang via Windsor.ai og arbejdsdeling](#5-adgang-via-windsorai-og-arbejdsdeling)
6. [Hvad det kræver af Lars (tid)](#6-hvad-det-kræver-af-lars-tid)
7. [Positionering, pris og sprog](#7-positionering-pris-og-sprog)
8. [Kanaler: de tre, de gratis ekstra og de nye](#8-kanaler)
9. [Indholdsmotoren](#9-indholdsmotoren)
10. [Test: Lars på kamera mod ansigtsløst](#10-test-lars-på-kamera-mod-ansigtsløst)
11. [Hjemmesiden som søgemaskine-aktiv](#11-hjemmesiden-som-søgemaskine-aktiv)
12. [Butikssiden (klar til copy/paste)](#12-butikssiden-klar-til-copypaste)
13. [Creators](#13-creators)
14. [Reddit, LinkedIn og lanceringsdagen](#14-reddit-linkedin-og-lanceringsdagen)
15. [Måling og beslutningsregler](#15-måling-og-beslutningsregler)
16. [Kalender](#16-kalender)
17. [Risici](#17-risici)
18. [Kilder og forbehold](#18-kilder-og-forbehold)

---

## 1. Ærligt udgangspunkt

TextFox er en Android-app til **7 USD**, engangskøb efter 30 dages gratis prøve, kun Android, kun SMS, budget 0 kr. Det giver en lille tragt:

- **Pr. salg lander ca. 5 USD (≈ 33 kr)** efter moms og Googles andel (EU-moms er med i butikspriserne, og Googles sats er ca. 15 % de første 1 mio. USD, men satserne ændrer sig, så tjek konsollen). **Early Bird-salg (3,50 USD) giver ca. 2,50 USD (≈ 16 kr).**
- **Betalt reklame er udelukket**, også hvis der kom budget: en install må højst koste et par kroner. 0 kr er altså det rigtige valg, ikke kun et vilkår.
- Salg kommer af tre ting i denne rækkefølge: **søgning** (Play-søgning, Google, YouTube "how to"), **anmeldelser** og **et par heldige videoer**. TikTok, Reels og Shorts er lodtrækningsbilletter, der er billige at lave (de rendres fra kode), og bevis for at appen er ægte. De er ikke motoren.
- Konkurrenterne ligger på 3,5–5 USD (Tasker, MacroDroid Pro, ifølge sammenligningssider), og Google Messages kan planlægge enkelte sms'er gratis. TextFox skal vinde på det, de ikke gør: gentagelser, auto-svar, ubesvaret opkald, AI-variation, widgets og "ingen konto, ingen reklamer".

**Tal (mine antagelser, ikke data).** Antagelser: 30 % af butiksbesøgende installerer, 5 % af installs køber (1,5 % køb pr. butiksbesøg), netto ca. 5 USD pr. salg.

| Butiksbesøg/md (efter måned 3) | Køb/md | Netto/md |
|---|---|---|
| 800 (lav) | 12 | ca. 60 USD ≈ 390 kr |
| 2.500 (base) | 38 | ca. 190 USD ≈ 1.240 kr |
| 8.000 (høj, kræver et gennembrud) | 120 | ca. 600 USD ≈ 3.900 kr |

- **Realistisk de første 90 dage uden viralt hit:** 300–1.000 installs og 15–50 køb, altså ca. 400–1.300 kr netto. Det er sideprojekt-niveau.
- **Hvis 0,2–1 % af videovisninger bliver til et butiksbesøg**, kræver 100 køb ca. 0,7–3,3 mio. visninger. Derfor bærer søgning og anmeldelser salget, og feedet er bonus.
- Salget halter ca. en måned efter installs på grund af de 30 gratis dage (dem med Early Bird-kode kan dog købe tidligere, hvis koden virker under prøven, se afsnit 4).
- **Det, der løfter loftet, er pris, segment og et gennembrud, ikke flere opslag.** Test en højere pris til erhvervssegmentet (ubesvaret opkald), hvor tilsvarende tjenester koster 15–100 USD om måneden hos leverandører.

---

## 2. Beslutninger fra Lars (22. nov)

| Emne | Beslutning |
|---|---|
| Pris og Early Bird | Appen koster **7 USD**. Koden **TEXTFOX50** giver 50 % (3,50 USD) **kun til dem, der køber med koden den første måned** (til 31. december kl. 23:00). |
| Konti | Alle konti (TikTok, YouTube, Instagram) er **private** og skal åbnes. **Reddit kan komme til**. Claude må foreslå nye kanaler (se afsnit 8). |
| Lars på kamera | **Ja, som test** for at se, hvad der virker bedst (afsnit 10). |
| Tid | "Den tid det skal til." Hvad der kræves, står i afsnit 6. |
| Adgang | **Windsor.ai** (afsnit 5). |
| Sprog | **Engelsk** til alt indhold. |

---

## 3. Status: hvad er gjort

| Hvad | Status |
|---|---|
| Denne plan | Skrevet (`marketing/PLAN.md`) |
| Skabelonside `/templates` med 8 skabeloner (9 makroer) som importfiler | Bygget. Filerne ligger i `website/public/macros/`, kilden er `website/src/templates.json`, og `website/tools/check_templates.py` tjekker dem mod appens egen importmodel (`MacroJson.kt`). Importen er endnu ikke prøvet på en rigtig telefon (se punkt 4). |
| Klip-pipeline til lodrette videoer (`marketing/shorts/`) | Bygget. To skabeloner ("macro" og "fox"), 8 klip i `clips.json`, musik og lyd syntetiseret fra kode, `make.sh` rendrer til `out/<id>.mp4` (ca. 1,5 min pr. klip) |
| Early Bird på hjemmesiden | Live (popup, menupunkt, forklaring, kode TEXTFOX50, udløber automatisk efter 31/12 kl. 23:00 dansk tid) |
| Go-links til måling | Klar som spec (afsnit 15). Bygges, når produktionslinket findes |
| Windsor.ai for Social Media | Forbundet (Trial) med TikTok og YouTube. **Instagram, Facebook-side, LinkedIn-side og Threads er ikke forbundet endnu.** Windsor kan poste Instagram Reels og Facebook Reels fra en offentlig video-URL, men ikke uploade til YouTube eller TikTok (afsnit 5) |
| Teaser-klip "Something clever is coming to Android" | Rendret (15,6 s) med cover og tekster, klar til upload (afsnit 9) |
| Konti åbnet og omlagt til business/creator | Mangler: Lars |

De rendrede klip ligger ikke i git (de kan genskabes præcist med `make.sh`). De 8 første klip leveres til godkendelse, før noget postes.

---

## 4. Porte: tjek før vi sender trafik

Intet i dette afsnit koster penge, men hver ting kan ellers lække salg. Ejer i parentes.

- [ ] **Produktionsadgang og SMS-erklæringen er godkendt** (Lars). Uden dem kan appen ikke sælges på Play med SMS. 12 testere i 14 dage er Googles regel for nye personlige konti.
- [ ] **Early Bird i Play Console** (Lars): tilbud på `textfox_unlock`, rabat 50 %, **tag `earlybird`**, 1 køb pr. bruger, **slutdato 31. dec. kl. 23:00 dansk tid**. Tjek tidszonen i konsollen. Slutdato skal være ens tre steder: Play-tilbuddet, `Date.UTC(2026, 11, 31, 22, 0)` i `website/public/assets/site.js` og teksterne på siden/i klippene.
- [ ] **Kan man købe med koden, mens de 30 gratis dage kører?** (Lars/Claude, test som licenstester.) Hvis ikke, kan ingen, der installerer efter ca. 1. december, nå at bruge koden. Så skal enten prøven kunne afkortes via koden, eller Early Bird-datoen rykkes.
- [ ] **En Play-promokode (til creators) låser appen op for evigt** (Lars, test med en rigtig kode). Googles hjælpeside nævner 250–500 koder pr. kvartal afhængigt af opsætning. Ubrugte koder forsvinder ved kvartalsskiftet, så vi bruger dem hellere end at gemme dem.
- [ ] **Import af skabeloner virker på en rigtig telefon** (Lars): download `running-late.json`, *Settings → Backup → Import*. Tjek at makroen kommer ind slukket og uden modtagere.
- [ ] **Anmeldelses-prompt i appen** (app-ændring, Lars/udvikling): Googles In-App Review API, efter fx anden vellykkede afsendelse eller dag 7. Ikke ved første start, ikke bag en knap, ikke efter en fejl.
- [ ] **Butikssiden opdateret** (Lars indsætter, tekst i afsnit 12): titel med søgeord, kort beskrivelse, "30 dage gratis, derefter engangskøb" tydeligt, dansk oversættelse.
- [ ] **Konti åbnet** (Lars): Instagram → Business/Creator (kan ikke være privat) knyttet til en Facebook-side; TikTok → Business (klikbart link i bio uden 1.000 følgere; kan kræve virksomhedsdokumentation under 1.000); YouTube-kanal med beskrivelse, banner og verificeret telefon. Læg 5–8 klip op, **før** profilerne linkes fra noget, så de ikke ser tomme ud.
- [ ] **Search Console** (Lars): sitemap `https://www.textfox.app/sitemap.xml` indsendt (domænet er verificeret via DNS).

---

## 5. Adgang via Windsor.ai og arbejdsdeling

### Hvad Windsor kan (`list_actions` kørt 22. nov. på "Windsor.ai for Social Media")

| Platform | Forbundet nu | Kan Windsor poste video? |
|---|---|---|
| **Instagram** | Nej. Mangler en Business/Creator-konto (via Facebook) | **Ja:** Reel fra en offentlig MP4/MOV-URL (3 s–15 min, ≤ 1 GB, 9:16 anbefalet, caption, cover), Story (video 3–60 s), billed- og karruselopslag, kommentarer (svar, skjul, slet), slet opslag |
| **Facebook-side** | Nej | **Ja:** Reel (offentlig URL, 3–90 s, 9:16, ≥ 540×960, 24–60 fps), videoopslag, tekst/foto-opslag (kan **planlægges** 10 min til 29 dage frem), Messenger-svar |
| **YouTube** | Ja (kanalen "TextFox": 0 abonnenter, ingen videoer, ingen beskrivelse) | **Nej.** Kun titel, beskrivelse, tags, kategori og synlighed på en video, der allerede er uploadet, plus kommentarer og playlister |
| **TikTok** | Ja (`textfox4`, visningsnavn "larssohl", ikke business, 0 følgere) | **Nej.** Ingen handlinger overhovedet. Kun statistik |
| **LinkedIn** | Nej | Kun en **virksomhedsside** (tekst, link, billede). Ingen video, ikke personlig profil |
| **Threads** | Nej | Tekst og billede, ingen video |

Windsor-planen er **Trial** (30 dages datahistorik). Prisen for en betalt plan er ikke verificeret (kilder er uenige), og 0 kr-budgettet gælder.

**Konsekvenser:**

- **Instagram og Facebook posteres af Claude** (efter Lars' godkendelse af hvert opslag), så snart de er forbundet. **YouTube og TikTok uploader Lars selv** (15 min/uge); Claude sætter YouTube-detaljer og læser tal.
- **Ingen af handlingerne har en kladde-tilstand.** Et Instagram- eller Facebook-opslag er offentligt med det samme (kan slettes igen med `delete_media`/`delete_post`). Første opslag er derfor selve teaseren.
- **Videoen skal ligge på en offentlig URL** (direkte til filen, uden login og uden redirect), for Instagram og Facebook henter den selv. Claude lægger de godkendte klip under `https://www.textfox.app/media/shorts/<id>.mp4` via en almindelig PR og rydder op i ældre filer.
- LinkedIn: kun virksomhedsside, så Lars' personlige opslag (afsnit 14) laves manuelt.
- To Windsor-connectors er forbundet (den almene og "for Social Media"). Den almene kan kobles fra, så der kun er én.

### Tre veje, billigste først

| Vej | Hvad | Pris |
|---|---|---|
| **A. Post-pakker** | Claude leverer færdige filer + tekst + hashtags + tidspunkt + første kommentar. Lars planlægger i YouTube Studio, Meta Business Suite og TikToks upload. | 0 kr, ca. 20 min/uge |
| **B. Lars' computer** | Lars forbinder sin computer, så Claude arbejder i hans indloggede Chrome. Lars godkender hvert skridt. | 0 kr, langsommere og skrøbeligt |
| **C. Windsor.ai for Social Media (valgt)** | Claude poster **Instagram Reels** og **Facebook Reels** direkte fra en offentlig URL, henter statistik og redigerer YouTube-detaljer. Uploader ikke til YouTube/TikTok. | Trial nu, betalt plan ikke verificeret |

### Sådan forbinder Lars Windsor (ca. 15 min)

1. claude.ai → **Settings → Connectors** → søg **"Windsor.ai for Social Media"** (ikke den almene "Windsor.ai") → **Connect**. Log ind hos Windsor.
2. Hos Windsor: forbind hver konto med OAuth (**Instagram** via Facebook-siden, TikTok, YouTube, evt. Facebook-siden, LinkedIn og Threads). TikTok og YouTube er forbundet; **Instagram (Business/Creator, knyttet til en Facebook-side) og Facebook-siden mangler**. Claude ser aldrig adgangskoder.
3. Sig til, så poster Claude teaseren som Instagram Reel og Facebook Reel (efter Lars' OK på caption og tidspunkt).

### Spilleregler

- **Intet postes, sendes som DM eller besvares uden Lars' OK.** Claude kladder, Lars godkender (20 min/uge).
- Ingen købte følgere, likes, visninger eller anmeldelser, ingen "engagement pods", ingen bots. Det kan koste både sociale konti og Play-kontoen.
- Ingen adgangskoder i chat. To-faktor slået til på alle konti. Adgang kan altid trækkes tilbage hos Windsor.
- Alle anmeldelser, kommentarer og DM'er besvares inden for 48 timer. Claude kladder svar, Lars godkender (de første uger; senere kan rutinesvar aftales).

### Ugerytme

| Dag | Hvem | Hvad |
|---|---|---|
| Man | Claude | Producerer ugens pakke (5 korte klip + 1 tutorial), skriver tekster, henter tal via Windsor og analyserer sidste uge |
| Tirs | **Lars** | Godkender pakken og tallene (20 min) |
| Ons | **Lars** | Uploader de godkendte filer til **YouTube Studio og TikTok** med de færdige tekster (15 min) |
| Ons–søn | Claude | Poster **Instagram Reel og Facebook Reel** (efter godkendelse), sætter YouTube-titel/beskrivelse/tags/synlighed via Windsor, når Lars har uploadet som privat, og kladder svar til kommentarer |
| Dagligt | **Lars** | 10 min Reddit/fora (afsnit 14) |
| Fre | Claude | Én side: tal, hvad der virkede, næste eksperiment |

Claude kan sætte rytmen op som en **ugentlig planlagt opgave**, så pakken står klar mandag.

---|---|---|
| Man | Claude | Producerer ugens pakke (5 korte klip + 1 tutorial), skriver tekster, analyserer sidste uge |
| Tirs | **Lars** | Godkender pakken og tallene (20 min) |
| Ons–søn | Claude | Opslag går live på de planlagte tidspunkter; svarkladder til kommentarer |
| Dagligt | **Lars** | 10 min Reddit/fora (afsnit 14) |
| Fre | Claude | Én side: tal, hvad der virkede, næste eksperiment |

Claude kan sætte rytmen op som en **ugentlig planlagt opgave**, så pakken står klar mandag.

---

## 6. Hvad det kræver af Lars (tid)

Lars har tiden, men vidste ikke hvad der kræves. Her er det ærlige billede:

| Fase | Timer | Indhold |
|---|---|---|
| **Uge 0** (opsætning, én gang) | ca. 3–4 t | Åbne og omlægge konti (45 min), forbinde Windsor (15 min), Play Console (Early Bird, butikstekst, 45 min), teste import/kode/promokode på telefon (45 min), optage første skærmklip (45 min) |
| **Uge 1–6** | ca. 2,5–3 t/uge | Godkendelse 20 min · optagelse 45–60 min (skærmklip + 1–2 klip med ansigt) · Reddit/fora 10 min/dag (≈ 60 min) · svar på anmeldelser/DM 30 min · tal 15 min |
| **Fra uge 7** | ca. 1,5 t/uge | Godkendelse, 1 optagelse, svar, tal. Resten kører (Claude) |

Det Claude tager sig af: klip, tekster, hashtags, planlægning, statistik, skabeloner, hjemmeside, outreach-kladder, analyse og ugerapport.
Det kun Lars kan: Play Console, kontoer og login, optagelser på egen telefon og på kamera, Reddit-kontoen, godkendelser og det, der kræver, at en rigtig person svarer.

---

## 7. Positionering, pris og sprog

**Ærlighed om Google Messages vinder tillid og fanger søgningen:** "Skal det kun ske én gang, så brug Google Messages (hold send nede). Skal det gentage sig, svare for dig eller reagere på opkald, så er det TextFox." Jeg kunne ikke finde, at Google Messages kan gentage planlagte beskeder; tjek selv, før det siges offentligt.

**Tre jobs i stedet for "SMS-automatisering":**

1. **Gentagne beskeder:** godmorgen, "er på vej", påmindelser til holdet.
2. **Ubesvaret opkald giver automatisk sms:** skarpest og mest betalingsparat for selvstændige (tilsvarende tjenester koster typisk 15–100 USD/md hos leverandører). Test det som eget segment (custom listing, egen landingsside, dansk "På stigen"-klip, 2 ugers outreach).
3. **Auto-svar** i møde, bil eller ferie.

**Pris:** 7 USD ligger over Tasker/MacroDroid Pro. Det kan forsvares med enkelhed, AI og "ingen konto", men vil kunne ses i konverteringen. Early Bird (3,50 USD) giver anmeldelser og fart i første måned. Efter 60 dage: test en **højere** basispris for erhvervssegmentet frem for en lavere for alle.

**Sprog:** engelsk til alt, der postes. "Android" i første linje af hver hook, så iPhone-brugere falder fra (Danmark er kun ca. 37 % Android, StatCounter, webtrafik, sept. 2026). Dansk bruges kun til hjemmesiden, LinkedIn/Facebook-grupper og pitch til danske medier, hvis Lars vælger det.

---

## 8. Kanaler

### De tre (Lars' valg)

| Kanal | Rolle | Detaljer |
|---|---|---|
| **YouTube** | Varig trafik: folk søger "how to…" | 6 tutorials på 2–4 min med Play-link i beskrivelsen. **Links i Shorts' beskrivelse og kommentarer er ikke klikbare** (siden aug. 2023), så Shorts peger på tutorialen via "relateret video", hvis feltet findes i Studio, ellers via profilen. |
| **TikTok** | Opdagelse og TikTok-søgning (søgeord i tekst og caption) | 3–5 klip/uge. Klikbart link i bio kræver business-konto (eller 1.000 følgere). Ifølge guides må business-konti kun bruge Commercial Music Library. Vores egen syntetiserede musik er licensfri. |
| **Instagram** | Samme Reels som TikTok (uden vandmærke) + highlights som dokumentation | Highlights: "How it works", anmeldelser, FAQ, templates |

### Gratis ekstra, samme fil (næsten ingen ekstra tid via Windsor)

- **Facebook-side (Reels):** forældre og selvstændige bruger både sms og Facebook. Claude poster Reels via Windsor, når siden er forbundet.
- **Threads:** Windsor kan kun tekst og billede, ikke video. Kort tekstopslag med link, eller Lars poster videoen selv.
- **LinkedIn** (Lars' profil): ubesvaret-opkald-historien til selvstændige og designere i netværket. Lars poster selv (Windsor kan kun en virksomhedsside og ingen video). Her må Lars gerne skrive dansk.
- **YouTube Community-opslag:** påmindelser om tutorials.

### Nye kanaler jeg foreslår (som ikke var på listen)

| Kanal | Hvorfor | Hvordan | Forbehold |
|---|---|---|---|
| **Reddit** (Lars' konto) | Folk spørger dagligt "how do I schedule a recurring text on Android?" | Byg karma først (uge 0–3): hjælpsomme svar uden links. Derefter dev-opslag med oplysning om, at du er udvikler. Se afsnit 14. | Læs hver subreddits regler. Jeg kunne ikke verificere r/androidapps' regler; r/Android henviser app-promo derhen og kræver selvopslag med detaljeret beskrivelse. Nye konti bliver ofte filtreret. |
| **Play-søgning (ASO)** | Højest intent. Alt andet ender på butikssiden. | Afsnit 12 | Nyt app rangerer lavt til at begynde med |
| **Hjemmesidens skabeloner + how-to** | Varig organisk søgetrafik | Afsnit 11 | Tager måneder at rangere |
| **AlternativeTo** | Gratis katalog, "alternativ til MacroDroid/Tasker" | Opret opslag efter lancering | Typisk dusinvis af besøg, men giver backlinks |
| **Product Hunt** | Én dag med omtale + backlink | Kun én gang, når der er rating og anmeldelser | Kræver community-opbakning; Android-forbruger-utility er ikke kernepublikum |
| **Android Central-forum (udviklertråd)** | Målgruppen er Android-brugere | Én tråd, svar på spørgsmål | Info jeg fandt er fra 2014 (krav om meningsfulde indlæg først). Tjek aktuelle regler |
| **XDA-forum** | Android-entusiaster | Udviklertråd | Aktuelle regler ikke verificeret |
| **Udviklerhistorier (dev.to / Hashnode)** | "Why your scheduled SMS doesn't fire on Xiaomi (and how I fixed it)": backlinks, troværdighed om pålidelighed | 1 artikel i måned 2 | Målgruppen er udviklere, ikke kunder |
| **Mobilsiden.dk** (Android-sektion) | Dansk Android-presse | Kort tip, ikke annonce: "dansk app, ingen konto, kører på telefonen" | Ingen garanti. Jeg har ikke verificeret redaktionens tipsadresse |
| **Facebook-grupper** (Android, selvstændige) | Dansk målgruppe, hvis Lars vil | Observér, bidrag, nævn aldrig appen uden regel-tjek | Mange grupper forbyder reklame |

### Droppet (med begrundelse)

Betalt annoncering (regnestykket i afsnit 1), influencer-honorarer (budget 0), Samsung Galaxy Store/Amazon (kræver separat betalingsintegration), Pinterest, X/Twitter og Discord/Telegram (lav målgruppematch eller moderationsarbejde for lidt udbytte), købte anmeldelser/følgere/"engagement pods" (forbudt, og ødelæggende).

---

## 9. Indholdsmotoren

**Fordelen:** vi har en kodebaseret film-pipeline (UI genskabt fra appens stil, rævemaskot, musik og lyd syntetiseret fra bunden). Klip kan batch-rendres uden kamera, og de er licensfri. Pipeline og kommandoer: `marketing/shorts/README.md`.

**Autenticitet:** tutorials og alt, der "viser appen", skal være **rigtige skærmoptagelser fra Lars' telefon**. Pipeline-klippene viser et genskabt UI i appens udtryk og bruges til korte hooks og skabeloner, ikke til at bevise, at noget virker. Mærk dem aldrig som skærmoptagelse.

### Serier

| Serie | Længde | Idé |
|---|---|---|
| **Steal this macro** | 15–25 s | Én færdig makro pr. klip. Download-fil på textfox.app/templates. Broen fra video til install. |
| **Fox says** | 10–15 s | Chat-sketch med ræven ("23:58. I forgot to text everyone Happy New Year." / "Relax. Already scheduled.") |
| **Eksperimenter** | 20–40 s | "I disappeared for 48 hours. My phone didn't." (Lars) · "Christmas texts to 40 people in 4 minutes" · "12 texts to my future self in 2027" |
| **Ærlig sammenligning** | 30–60 s | Google Messages vs TextFox vs MacroDroid |
| **Bygge i det åbne** (Lars) | 20–40 s | "Launching an Android app with 0 kr: week 1 numbers" |
| **På stigen** (dansk, til LinkedIn/Facebook) | 15–25 s | Kunden får en sms, før de ringer til konkurrenten |

### Første batch (9 klip, rendret)

`running-late`, `good-morning-ai`, `missed-call`, `driving`, `out-of-office` (skabelon "macro"), `fox-new-year`, `fox-ladder`, `fox-mom` (skabelon "fox") og **`teaser`** (skabelon "teaser": ræven vågner i mørket, "Something clever is coming to Android", CTA "Coming soon on Google Play · textfox.app · Early-bird deal at launch"; 15,6 s, plus `teaser-cover.jpg`). Alle slutter med CTA (textfox.app/templates, kode TEXTFOX50 til 31/12). Caption, hashtags, YouTube-titel og første kommentar står i `clips.json` under `post`. **Efter 31/12 sættes `"promo": null` i `clips.json`, og klippene rendres igen uden rabatlinjen.**

### Hook-bibliotek (engelsk, "Android" først)

1. Android users: your phone can text "running late" for you.
2. I never remember to text my mom good morning. My phone does.
3. POV: you scheduled New Year's wishes to 40 people and went to sleep.
4. Your boss texts you on vacation. Your phone answers.
5. Customers call while you're on a ladder. Your phone texts back.
6. Don't text while driving. Let your phone answer.
7. Google Messages can send one text later. This sends it every week.
8. 3 texts you should never type again (Android).
9. I set my out-of-office to switch itself off on my last day.
10. This free file adds a missed-call text-back to your Android phone.

### Regler for alt indhold

- "Android" i første linje, én CTA pr. klip, ingen vinkler om at "lade som om man er til stede", og aldrig massebeskeder (Play-politik og brandrisiko).
- Tekst på skærmen i sikkerhedszonen (ikke de nederste ca. 450 px og ikke yderst til højre, hvor platformenes knapper ligger). Pipeline'en overholder det.
- 5 korte klip pr. uge i 8 uger, derefter beslutning (afsnit 15).

### YouTube-tutorials (rigtig skærmoptagelse, 2–4 min)

| # | Titel | Søgeord |
|---|---|---|
| 1 | How to schedule a recurring text message on Android | recurring text android, schedule text weekly |
| 2 | Auto reply to texts on Android (driving, meetings, vacation) | auto reply text android |
| 3 | Missed call text back on Android (no subscription) | missed call text back android |
| 4 | One-tap "running late" text from your home screen | home screen widget text |
| 5 | Schedule New Year's texts to everyone at midnight | schedule text new year |
| 6 | Get a free Gemini key and let AI write your replies | free gemini api key (filmen findes) |

### Optageprotokol til Lars (45–60 min/uge)

- Optag telefonens skærm i 9:16 med **Android skærmoptagelse**, ét emne ad gangen, uden notifikationer fra privat indhold (brug demo-kontakter og fiktive 555-numre).
- Optag stille. Claude lægger tekst, musik og lyd på.
- Til klippene med ansigt: 15–25 s, Lars taler direkte, ét budskab, naturligt lys. Claude skriver manuskript og tekster.

---

## 10. Test: Lars på kamera mod ansigtsløst

**Formål:** finde ud af, hvilket format der giver flest butiksbesøg pr. produktionstime, ikke flest visninger.

| Format | Beskrivelse | Hvem laver det |
|---|---|---|
| **F1 Ansigtsløst** | Klip fra pipeline'en ("Steal this macro") | Claude |
| **F2 Maskot** | "Fox says"-sketcher | Claude |
| **F3 Lars** | 15–25 s til kamera med skærmoptagelse ("I built this because…", "3 texts I never type anymore") | Lars + Claude klipper |

**Design:** 4 uger, mindst 8 opslag pr. format, samme CTA, samme tidspunkter, rækkefølgen blandes. Platformene scores hver for sig (TikTok, Reels, Shorts).

**Mål pr. opslag** (hentes via Windsor eller manuelt ind i `marketing/tracker.csv`): visninger efter 48 t, gennemsnitlig visningstid i %, delinger, gem, profilbesøg, linkklik (via go-links), installs.

**Beslutning efter 4 uger:** formatet med flest linkklik og installs pr. opslag *og* pr. produktionstime vinder. Er forskellen mindre end støjen (færre end 8 opslag eller få klik), kører vi en uge mere i stedet for at gætte.

---

## 11. Hjemmesiden som søgemaskine-aktiv

**Live nu:** `/templates` med 8 skabeloner (9 makroer), hver som en lille JSON-fil, appen kan importere (*Settings → Backup → Import*): Running late, Good morning, Good morning (AI-fresh), Out of office, Driving, Missed-call text-back, Weekly reminder, Quiet nights. Filerne kommer ind **slukket og uden modtagere**, så intet kan sendes, før man har set på dem.

**Sådan tilføjes en skabelon:** ret `website/src/templates.json` (ét objekt), kør `python3 website/build.py` og `python3 website/tools/check_templates.py` (den afviser manglende felter og ugyldige værdier mod `MacroJson.kt`).

**Næste sider (Claude, i den rækkefølge):**

1. Ærlig sammenligningsside: *TextFox vs Google Messages vs MacroDroid* (EN).
2. How-to-artikler til de seks tutorials (søgeord ovenfor), hver med skabelon-download.
3. 10–20 flere skabeloner (fx "Rent reminder", "Class reminder", "Birthday text") hvor appen kan det; undgå dem, appen ikke kan (fx månedlig gentagelse).
4. Dansk spejl af `/templates` (`/da/skabeloner`), hvis Lars vil bruge den danske flade.

**Go-links** (se afsnit 15) og **Search Console** følger med.

---

## 12. Butikssiden (klar til copy/paste)

> Reglen: **ingen rabat- eller salgsudtryk i titel, ikon og grafik** (Googles metadataregler). Hold "50 % Early Bird" til hjemmesiden og klippene. Tjek altid feltet i konsollen, før du indsætter.

**Title (≤ 30 tegn, nu "TextFox — SMS Automation"):**
```
TextFox: Schedule & Auto SMS
```
(28 tegn.)

**Short description (≤ 80 tegn, 73 tegn):**
```
Schedule texts, auto-reply and missed-call text-back. No account, no ads.
```

**Første afsnit af Full description (tilføj øverst, forhindrer pris-skuffelse):**
```
TextFox sends your texts for you: scheduled messages, auto-replies and missed-call text-back.
Try everything free for 30 days, then unlock it with a one-time purchase. No account, no ads, no subscription.
```

**Dansk (da-DK):**

Title:
```
TextFox: Planlæg og autosvar
```
(28 tegn.)

Short description (79 tegn):
```
Planlæg sms, autosvar og sms ved ubesvaret opkald. Ingen konto, ingen reklamer.
```

Første afsnit:
```
TextFox sender dine sms'er for dig: planlagte beskeder, autosvar og sms ved ubesvaret opkald.
Prøv alt gratis i 30 dage, og lås det derefter op med ét engangskøb. Ingen konto, ingen reklamer, intet abonnement.
```

**Søgeord at have i teksten (naturligt, ikke remset op):** schedule text message, recurring text, auto reply sms, missed call text back, sms scheduler, automatic text, text automation.

**Skærmbilleder:** 5 findes (`store/screenshots/`). Overvej at åbne med udbytte ("Texts that send themselves"), ikke funktion. Brug **custom store listings** (op til 50, hver med eget link) til: forbrugere (godmorgen/running late), erhverv (ubesvaret opkald), creators.

**Butikseksperimenter:** slå dem til, når der er trafik nok (Play kræver nok besøg til statistisk sikkerhed).

---

## 13. Creators

Siden `/creators` findes og tilbyder allerede en personlig Play-promokode (låser op for evigt) + presskit. Det mangler: **henvendelserne**.

**Mål:** 40 personlige henvendelser → forventet 3–6 videoer. Gratis kode koster ingen kontanter, men koder er begrænsede pr. kvartal (tjek konsollen), så vælg dem med publikum.

**Hvem (mikro, 1.000–50.000 følgere):** Android-tips og app-anmeldere, forældre-/familiekanaler, selvstændige og solopreneurs, hjemmeautomatik (Home Assistant), danske tech-skabere (til LinkedIn/Facebook-vinklen).

**Sådan finder vi dem:** søg YouTube efter "best Android automation apps", "MacroDroid tutorial", "Tasker beginners", "auto reply text Android", søg TikTok efter #androidtips #androidapps #androidhacks, og tjek hvem der kommenterer på sammenlignende videoer. Claude samler listen (kanal, antal følgere, seneste video, kontaktmetode). Ingen kontakt uden Lars' OK.

**Mail (engelsk):**
```
Subject: A free TextFox code for your Android content?

Hi {name},

I liked your video on {video title}. I'm Lars, the maker of TextFox, an Android app that sends texts for you on a schedule, as auto-replies and as missed-call text-back (no account, no ads).

If it fits your channel, I'd love to send you a personal promo code that unlocks the app for good, plus a press kit with films, logos and screenshots: https://www.textfox.app/creators

No strings attached, and honest opinions (including "not for me") are welcome. If you do post something, please label it as a gift or ad as the rules require.

Lars
hello@textfox.app
```

**Kort DM:**
```
Hi {name}! I make TextFox, an Android app that texts for you (schedules, auto-replies, missed-call text-back). Happy to send you a free lifetime code if you'd like to try it on {channel}. Details: textfox.app/creators
```

**Regler:** koder og gaver skal mærkes (reklame/gave) efter markedsføringsloven, ingen krav om positiv omtale, ingen betaling for anmeldelser, intet "giv 5 stjerner". Følg op én gang efter en uge, derefter ikke mere.

---

## 14. Reddit, LinkedIn og lanceringsdagen

### Reddit (Lars' konto)

1. **Uge 0–3: bidrag uden at nævne appen.** 10 min/dag: besvar spørgsmål i fx r/AndroidQuestions og r/androidapps om sms, auto-svar, planlægning. Det bygger karma og kendskab til reglerne. (Subreddit-navne og regler skal tjekkes i sidebar/regler hos hver; jeg har ikke kunnet verificere dem.)
2. **Lanceringsdag:** ét dev-opslag i en subreddit, der tillader det, med oplysning om at du er udvikleren. Ikke på flere samtidig.
3. **Derefter:** svar på relevante tråde, hvor appen faktisk er et godt svar, nævn alternativer ærligt, max 1–2 opslag om måneden om appen.

**Udkast til lanceringsopslag (engelsk, tilpas til subredditten):**
```
Title: I made an Android app that sends texts for you: schedules, auto-replies and missed-call text-back (dev here)

Hi all, I'm the developer. TextFox lets you set up "macros": a trigger (a schedule, a home-screen widget tap, an incoming text, or a missed call) and an action (usually an SMS). Everything runs on the phone: no account, no ads, no subscription.

What I think is different: repeating schedules (every Mon–Fri, every 2 weeks, random days), auto-reply with sender/keyword filters and quiet hours, missed-call text-back, delivery receipts and a full log, and optional AI variations so a repeating text isn't worded the same twice (you bring your own free Gemini key, off by default).

Honest bits: Android only, SMS only (no WhatsApp). Google Messages can already send a single text later; this is for the repeating and the automatic. 30 days free, then a one-time 7 USD unlock. Early-bird code TEXTFOX50 gives 50% off until Dec 31.

There are free importable macro templates here: https://www.textfox.app/templates
Happy to answer anything, and I'd love to hear what's missing.
```

### LinkedIn (Lars, engelsk med mulig dansk kortversion)

```
I just launched TextFox, an Android app that sends texts for you.

The idea started with a simple annoyance: the same "running late" text, the same "I'll call you back" after a missed call. Set it up once and it just runs, on your own phone, with no account.

If you're a tradesperson, a freelancer or just always on the move, the missed-call text-back is the feature people ask about most. It costs 7 USD once (30 days free), and there's a free set of ready-made macros you can import: textfox.app/templates

Early-bird code TEXTFOX50 = 50% off until Dec 31.
```

Dansk kortversion: "Jeg har lanceret TextFox, en Android-app der sender sms'er for dig: planlagt, som autosvar og ved ubesvaret opkald. Ingen konto, ingen reklamer. 30 dage gratis, derefter engangskøb. Gratis skabeloner: textfox.app/templates. Early Bird-kode TEXTFOX50 giver 50 % til 31. dec."

### Lanceringsdag (runbook)

| Tid | Hvem | Hvad |
|---|---|---|
| T-1 dag | Lars | Alt i afsnit 4 er afkrydset. Konti er åbne med 5–8 klip. |
| T-0 morgen | Claude | Go-links bygget med produktionslinket (`PLAY_URL` i `build.py`, JSON-LD, `llms.txt`), site live, Early Bird-tekster tjekket |
| T+0 | Claude/Lars | "It's live" klip + opslag på alle tre + Facebook/Threads/LinkedIn. Lars: Reddit-dev-opslag + LinkedIn |
| T+0 | Lars | Bed de 12 testere om **ærlige** anmeldelser (aldrig om stjerner) |
| T+0 til T+7 | Claude | Svarkladder til alle kommentarer og anmeldelser, ugerapport |
| T+7 | Begge | Ret de 3 hyppigste klager; først derefter mere trafik |

---

## 15. Måling og beslutningsregler

### Go-links (spec, bygges ved lancering)

`https://www.textfox.app/go/<kilde>` → 302 til Play-listingen med UTM i `referrer`:

```
https://play.google.com/store/apps/details?id=app.kodekid.automatiq&referrer=utm_source%3D<kilde>%26utm_medium%3Dsocial%26utm_campaign%3Dlaunch
```

Kilder: `tiktok`, `youtube`, `instagram`, `facebook`, `threads`, `linkedin`, `reddit`, `alternativeto`, `producthunt`, `c/<creator>`, `yt-1` … `yt-6`. Genereres fra én liste i `build.py` ud fra `PLAY_URL`, så lanceringen kun skifter ét link. Ingen cookies, ingen analytics, så privatlivspolitikken er uændret. Play Console viser installs pr. kilde under "Tracked channels (UTM)", men **kun når linket åbner i Play-appen** (ikke i en desktop-browser).

### Tal, vi følger (ugentligt)

- **Nordstjerne:** betalte køb pr. uge.
- Butiksbesøg og installs pr. kilde (UTM), **butiks-konvertering** (sammenlign med Play Consoles peer-benchmark), retention efter 7 dage, afinstallationer, rating og antal anmeldelser, Early Bird-køb (antal og andel af alle køb).
- Pr. opslag: visninger efter 48 t, gns. visningstid i %, delinger, profilbesøg, linkklik. Samles i `marketing/tracker.csv` (Windsor kan fylde den).
- Search Console: visninger og klik pr. forespørgsel for `/templates` og feature-sider.

### Beslutningsregler (mine forslag, justeres med data)

- **Dag 14 efter lancering:** rating ≥ 4,3, ≥ 10 anmeldelser og butiks-konvertering mindst på peer-median. Ellers fixes butiksside/produkt, **før** vi skubber mere trafik.
- **Dag 45:** hvis intet opslag har >10.000 visninger, skifter vi format (ikke platform). **Efter 40 klip uden ét over 20.000:** short-form ned til 2/uge, timer flyttes til YouTube og søgning.
- **Dag 60:** kanalen med flest installs pr. produktionstime får dobbelt indsats. Test højere basispris for erhverv.
- **Efter 31/12:** fjern rabatlinjen (`"promo": null`), pop-up og menupunkt udløber selv, og hero-linjen og CTA-teksten på siden fjernes manuelt (`build.py`: `EB`, `CTA_NOTE`, `{{EB_LINE}}`).

---

## 16. Kalender

I dag er søndag 22. nov. Early Bird slutter torsdag 31. dec. kl. 23:00 (ca. 5,5 uge).

| Hvornår | Fokus | Konkret |
|---|---|---|
| **Nu → produktionsgodkendelse (uge 0)** | Gør klar, **ingen trafik til Play endnu** | Afsnit 4 afkrydset, Windsor forbundet, konti åbne, de 8 første klip godkendt og i banken, creator-liste, Reddit-karma starter, teasers "kommer snart + Early Bird" til textfox.app |
| **Lanceringsdag + uge 1** | Rolig, målt start | Link på alle profiler, testerne anmelder, Reddit/LinkedIn, første 10 creator-mails, svar på alt, ret de 3 hyppigste klager |
| **Uge 2–6, til 31/12** | Early Bird-sprint | 5 klip + 1 tutorial/uge, "Christmas texts" (17.–23. dec.), nytårsstunt (28.–31. dec.), nedtælling "7 dage / 48 timer / last call", AlternativeTo |
| **Januar** | Nytårsforsæt | Træning, vaner, "text to future me". Product Hunt hvis rating ≥ 4,5 og ≥ 20 anmeldelser. Evaluering 31/1. |
| **Feb–marts** | Dyrk det, der virker | Dobbelt op på bedste kanal, erhvervstest (ubesvaret opkald), pristest, dev-artikel, flere sprog på butikssiden |

**Hvis lanceringen glider efter ca. 10. december:** ryk Early Bird-slutdatoen (Play-tilbud, `site.js`, tekster) i stedet for at have under 3–4 ugers vindue.

---

## 17. Risici

| Risiko | Imod |
|---|---|
| SMS-erklæringen afvises → appen kan ikke sælges på Play med SMS | Porten i afsnit 4. Fallback er APK-sporet |
| Baggrundsdrab på Xiaomi/Samsung giver 1-stjernede anmeldelser | Guidet setup i appen, hurtige svar, ærlig forventningsafstemning i butikssiden |
| Prisskuffelse efter prøven | "30 dage gratis, derefter engangskøb" i butiksteksten og i klippene |
| Early Bird rammer ikke dem, der installerer efter ca. 1. dec. | Afsnit 4: test køb under prøven, ellers ryk datoen |
| Misbrug (spam, chikane) og juridisk risiko | Markedsfør kun svar og påmindelser, aldrig masseudsendelser (GDPR/ePrivacy; i USA TCPA) |
| Platformregler for nye konti | Ingen købte følgere, én konto pr. platform, mærk gaver |
| Windsor kan ikke uploade til YouTube/TikTok (bekræftet 22/11), og Instagram/Facebook har ingen kladde-tilstand. En betalt plan er ikke verificeret | YouTube/TikTok manuelt (vej A). Instagram/Facebook: godkend caption først, og slet opslaget, hvis noget er galt |
| Lars' tid | Færre ting godt frem for mange halvt. Rytmen i afsnit 5 |

---

## 18. Kilder og forbehold

Kilder jeg har brugt (web, 22. nov. 2026). Flere er sammenligningssider eller leverandør-sider, så tal og priser er vejledende og skal tjekkes mod primære kilder, før de bruges til beslutninger.

- Google Play, [12 testere i 14 dage](https://support.google.com/googleplay/android-developer/answer/14151465)
- YouTube, [links i Shorts er ikke klikbare](https://support.google.com/youtube/answer/13748639)
- Play Console, [acquisition og UTM](https://support.google.com/googleplay/android-developer/answer/9859173)
- Play Console, [promokoder](https://support.google.com/googleplay/android-developer/answer/6321495) (kilder er uenige om grænser; tjek din konsol)
- [Custom store listings (op til 50)](https://android-developers.googleblog.com/2023/11/power-your-growth-on-google-play.html)
- [Play In-App Review API](https://developer.android.com/guide/playcore/in-app-review)
- [Android-andel i Danmark, StatCounter](https://gs.statcounter.com/os-market-share/mobile/denmark)
- [TikTok link i bio, krav](https://buffer.com/library/how-to-put-a-link-in-tiktok-bio/) (vendor-guides, tjek i appen)
- [Google Messages: planlæg sms](https://www.androidauthority.com/schedule-text-google-messages-android-1182844/)
- [MacroDroid-pris, AlternativeTo](https://alternativeto.net/software/macrodroid/about)
- [Missed-call text-back, prisniveau](https://quo.com/blog/missed-call-text-back-software)
- [Windsor.ai prisoversigt (tredjepart)](https://blog.coupler.io/windsor-ai-pricing-overview/amp/)
- [Android Central, udviklerregler](https://forums.androidcentral.com/help/developerrules) (gammel info)
- [Mobilsiden.dk, Android-nyheder](https://mobilsiden.dk/nyheder/android-ios)

**Ikke verificeret:** r/androidapps' regler, XDA og Android Central-forummets nuværende udviklerregler, om Instagram og Facebook accepterer vores MP4 uden problemer (afprøves med første opslag), hvad en betalt Windsor-plan koster, Google Messages' evne til at gentage beskeder, præcis Play-gebyrsats og promokode-grænse, TikToks aktuelle krav til business-konti. Alt dette står som tjek i planen, ikke som fakta.
