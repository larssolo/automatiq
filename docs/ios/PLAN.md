# TextFox til iPhone — plan

Sidst opdateret 24. november 2026 (aften). Ejer: Lars.

## 1. Kort fortalt

iPhone-udgaven bygges fra bunden i Swift og SwiftUI. Den har samme navn, ræv, farver og pris som Android-appen. iOS tillader ikke, at en app selv sender SMS i baggrunden, læser indgående SMS eller ser mistede opkald. Derfor gør TextFox til iPhone det sådan:

- **To tryk (virker for alle, ingen opsætning):** På sendetidspunktet kommer en notifikation. Et tryk åbner den færdige besked med modtagere og variabler udfyldt, eventuelt AI-omskrevet. Et tryk på Send sender den.
- **Fuldautomatisk (via Genveje):** TextFox leverer handlinger til Genveje-appen ("Hent TextFox-besked", "Kør TextFox-makro"). En automatisering i Genveje kører på tidspunktet, eller når en SMS kommer ind. Den henter teksten fra TextFox og sender den med Genvejes egen "Send besked" uden at spørge. Appen guider brugeren trin for trin.

Det skal bevises på en rigtig iPhone, før vi bygger færdigt (milepæl 1).

## 2. Funktioner: Android → iPhone

| Android | iPhone | Hvordan |
|---|---|---|
| Planlagte SMS (tid, ugedage, hver N. uge, slutdato, tilfældig tid/dage) | ✅ | Samme regler (kopieret og testet i TextFoxCore). Notifikation med to tryk, eller automatisering i Genveje |
| AI-variation, AI-svar, AI-skriv (Gemini, egen nøgle) | ✅ | Samme prompts, kaldes direkte fra appen |
| Manuel / ét tryk | ✅ | Knap i appen og interaktiv widget, der åbner den færdige besked |
| Autosvar på SMS | ⚠️ kun via Genveje | Automatiseringen "Når jeg får en besked fra/indeholder…" giver beskeden videre til TextFox, som svarer |
| Mistet opkald | ❌ | iOS giver ingen adgang til opkald. Vises ikke på iPhone |
| Opladning / Wi-Fi | ⚠️ kun via Genveje | Genveje har udløsere for opladning og Wi-Fi |
| Bluetooth | ⚠️ via Genveje (Bluetooth-udløser) | Som ovenfor |
| Sted (ankomst/afgang) | ✅ | Notifikation ved ankomst eller afgang (Core Location) med to tryk, eller Genveje |
| Variabler {dato} {tid} {ugedag} {navn} {modtager} {afsender} | ✅ | Samme funktion |
| Kontaktnavne | ✅ | Contacts-tilladelse |
| Mapper, sortering, farver, søgning | ✅ | |
| Log | ✅ med forbehold | iOS fortæller ikke, om en SMS blev leveret. Loggen viser "sendt via Genveje", "åbnet" eller "annulleret" |
| Stille timer | ✅ | |
| Widgets | ✅ | WidgetKit, interaktive knapper (iOS 17) |
| Backup/import (JSON) | ✅ | **Samme filformat som Android.** Man kan flytte sine makroer fra Android til iPhone |
| 30 dages prøve, så 7 USD én gang, rabatkode | ✅ | StoreKit 2, se afsnit 5 |
| Lyd-/webhook-handlinger | Senere | Webhook er nemt, lyd/Forstyr ikke kan ikke styres af apps |

## 3. Opbygning

```
ios/
  project.yml              XcodeGen: beskriver projektet (selve .xcodeproj genereres)
  Config/                  Team ID til signering (lokal fil, ikke i git)
  Packages/TextFoxCore/    Ren logik uden iOS: model, planlægning, skabeloner,
                           matchning, prøveperiode, backup-format. 26 tests
  TextFox/                 SwiftUI-appen
  TextFoxWidget/           Widget-udvidelsen
  scripts/bootstrap.sh     Tjekker Mac'en, genererer, tester og bygger
```

- **iOS 17+** (interaktive widgets, nye App Intents, SwiftData).
- **Data:** én JSON-fil i en App Group (`group.app.kodekid.textfox`), så appen, widgetten og Genveje-handlingerne deler makroerne. Makroerne bruger samme format som Android-backuppen (valgt i stedet for SwiftData: enklere, og import/eksport er gratis).
- **Bundle ID:** `app.kodekid.textfox` (widget: `.widget`).
- **Ingen tredjepartsbiblioteker.** Gemini kaldes med URLSession. Nøglen gemmes i nøgleringen (Keychain).

## 4. Milepæle

Hver milepæl ender med en build, der virker, tests der består, og en test på Lars' iPhone.

1. **Bevis (1–2 dage).** En "Hent TextFox-besked"-handling i Genveje, der returnerer en udfyldt og AI-varieret tekst. Lars laver én automatisering, "Hver dag kl. 8 → Hent besked → Send besked", og den skal sende uden at spørge. Desuden notifikation → færdig besked → Send. **Hvis automatiseringen ikke kan køre uden bekræftelse, stopper vi og tænker om.**
2. **Makroer (3–4 dage). ✅ Bygget 24. nov. og testet i simulatoren.** Liste med mapper, kort og farver, editor med alle felter for de understøttede typer, kontakter, variabler, søgning, slet/dupliker/fortryd. Lagring i SwiftData.
3. **Planlægning og notifikationer (2–3 dage).** Lokale notifikationer for de næste 64 afsendelser (iOS-loft), som fyldes op igen, når appen åbnes, og i baggrundsopdatering. Notifikationshandlingerne Send/Rediger/Spring over. Stille timer. Log.
4. **Genveje og widgets (3–4 dage).** App Intents: hent besked, kør makro, autosvar på indgående besked (input: afsender og tekst). Guide i appen med billeder til hver automatisering. Interaktiv widget pr. makro.
5. **AI (2 dage).** Gemini-klient, nøgletest, modelvalg, AI-svar/variation/skriv, godkend eller send automatisk. Samme reserveløsning som Android: fast tekst, hvis AI fejler.
6. **Køb (2 dage).** StoreKit 2, prøveperiode, oplåsning, gendan, rabatkode (Offer Code), banner og påmindelser.
7. **Import, sted, finpudsning (2–3 dage).** Import/eksport af Android-backup, steds-makroer, onboarding med ræve-intro, Sundhed-skærm (notifikationer, Genveje-status), tilgængelighed og dansk/engelsk tekst.
8. **Udgivelse (1–2 dage + Apples gennemgang).** App Store Connect-oplysninger, skærmbilleder, privatlivsoplysninger, TestFlight til Lars og testere, indsendelse.

I alt ca. 3–4 ugers arbejde plus Apples gennemgang.

## 5. Pris og køb på iPhone

- Engangskøb (non-consumable) `textfox_unlock`, 7 USD.
- **Prøveperiode:** Apple har ingen indbygget prøve for engangskøb. Apple tillader dette: appen er gratis, og der er et gratis køb ("30-day free trial", non-renewing subscription til 0 kr.), som starter prøven. Derefter købes oplåsningen. Prøven tælles i kalenderdage som på Android (TextFoxCore `licenseStatus`).
- **Rabatkode:** Apples Offer Codes til engangskøb, eller en separat rabat-vare. Afklares i milepæl 6.
- Køb på Android og iPhone deles ikke. Det er to butikker.

## 6. Risici

| Risiko | Hvad vi gør |
|---|---|
| En automatisering i Genveje kræver bekræftelse ved "Send besked" | Testes først (milepæl 1). Ellers bliver to tryk standarden, og Genveje er en ekstra mulighed |
| Det er besværligt at oprette automatiseringer | Trin for trin-guide i appen med skærmbilleder. Én automatisering pr. tidspunkt; makroer med samme tidspunkt deler den |
| Apple afviser appen ("for lidt funktion" eller SMS-politik) | Tydelig værdi uden Genveje (to tryk, AI, widgets). Ingen påstand om "automatisk SMS" uden Genveje i App Store-teksten |
| Loftet på 64 ventende notifikationer | Kun de næste afsendelser planlægges, og listen fyldes op igen løbende |

## 7. Hvad Lars skal gøre

1. Åbne Xcode én gang: acceptér licensen, og lad det installere komponenter, også iOS-simulatoren.
2. Xcode → Settings → Accounts: tilføje dit Apple ID (det med udviklermedlemskabet).
3. Finde dit **Team ID** på developer.apple.com → Account → Membership.
4. På iPhonen: Indstillinger → Anonymitet og sikkerhed → **Udviklertilstand** til, og forbinde telefonen med kabel og vælge "Har tillid".
5. Senere: oprette appen i App Store Connect (navn, bundle-ID `app.kodekid.textfox`) og købene.
