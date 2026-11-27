# TextFox til iPhone: App Store

Sidst opdateret 27. november 2026. Det her er det, Lars skal gøre i App Store Connect, og teksterne der skal ind.

## 1. Opret appen

App Store Connect → Apps → **+** → New App:

- Platform: iOS
- Name: **TextFox — Texts on Autopilot** (højst 30 tegn; "TextFox" alene kan være taget)
- Primary language: English (U.S.)
- Bundle ID: **app.kodekid.textfox** (vælg det på listen; Xcode opretter det første gang appen bygges signeret)
- SKU: `textfox-ios`

## 2. Køb (In-App Purchases)

Features → In-App Purchases → **+**. Alle tre er **Non-Consumable**:

| Product ID | Reference name | Pris | Visningsnavn / beskrivelse |
|---|---|---|---|
| `textfox_trial` | 30-Day Free Trial | **Free** (0) | "30-Day Free Trial" / "Try every TextFox feature free for 30 days." |
| `textfox_unlock` | Unlock TextFox | 6,99 USD (nærmeste pristrin til 7 USD) | "Unlock TextFox" / "Unlock TextFox once. No subscription." |
| `textfox_unlock_earlybird` | Unlock TextFox Early Bird | 3,49 USD | "Unlock TextFox (early bird)" / "Unlock TextFox once, early-bird price." |

- Apples regel (3.1.1): en prøveperiode før et engangskøb skal være et gratis køb, der hedder noget med "Trial". Derfor `textfox_trial`.
- Early-bird-varen vises kun, når man har tastet koden **TEXTFOX50** i appen (samme kode som på Android).
- Hvert køb skal have et screenshot af købsskærmen til Apples gennemgang (brug simulatoren: Indstillinger → TextFox → Unlock).

## 3. App-oplysninger

- Category: **Productivity** (sekundær: Utilities)
- Age rating: 4+ (ingen af spørgsmålene er ja)
- Price: Free (købene står for betalingen)
- Support URL: https://textfox.app
- Marketing URL: https://textfox.app
- Privacy Policy URL: https://textfox.app/privacy.html (findes på hjemmesiden; tjek at den også nævner iPhone)
- Copyright: 2026 Lars Sohl

### App Privacy (privatlivsetiketten)

Svar: **Data Not Collected**. TextFox har ingen server. Makroer, log og indstillinger ligger kun på telefonen. AI-tekst går direkte fra telefonen til Google med brugerens egen nøgle, og kun hvis brugeren har slået "Allow AI" til; det er ikke data, *udvikleren* indsamler. Appen har en privacy manifest (ingen tracking).

## 4. Tekster (engelsk)

**Subtitle (30):** Scheduled texts & auto-replies

**Promotional text (170):** Write a text once — TextFox has it ready on time, every time. Weekdays, every other week, random days, AI rewrites and auto-replies. Free for 30 days.

**Keywords (100):** sms,schedule,text,message,reminder,auto reply,later,timer,shortcuts,automation,ai,birthday,send

**Description:**

> TextFox puts your regular texts on autopilot.
>
> Good morning to Mom on weekdays. "Bedtime in 15 minutes" every other week. A reminder to take your vitamins. Write the message once, pick when, and TextFox has it ready at the right time — every time.
>
> ONE TAP TO SEND
> At the send time you get a notification with the finished message: recipients, date, weekday and names filled in. Tap Send and it's gone.
>
> NO TAP AT ALL
> Add the TextFox Send shortcut once and switch it on. From then on it starts by itself with every TextFox notification and sends the message — scheduled texts and arrivals alike (iOS 27). Auto-replies work through a Shortcuts automation too; the guide in the app shows how.
>
> SCHEDULE IT YOUR WAY
> • Weekdays, weekends or any days you pick
> • Every 2, 3 or 4 weeks
> • Only some of the days, picked at random each week
> • A slightly different time each day
> • Once on a date, or until an end date
>
> AI THAT SOUNDS LIKE YOU
> With your own free Google Gemini key, every send can be a fresh rewrite so it never reads the same twice — and auto-replies can answer in your words. No AI fees, no account, no server.
>
> AND MORE
> • Auto-replies for texts from a person or with a word
> • Messages when you arrive at or leave a place
> • Variables: date, time, weekday, name
> • Folders, colours, search and a log of everything sent
> • Home-screen widget: send a macro with one tap
> • Moves your macros from TextFox on Android (same backup file)
>
> FAIR PRICING
> Free for 30 days. Then unlock TextFox once — no subscription.

**What's New (1.0):** The first version of TextFox for iPhone.

## 5. Screenshots

6,9" (iPhone Pro Max, 1320 × 2868) ligger i `docs/ios/screenshots/`. Lav dem igen med `ios/scripts/screenshots.sh` (bruger demo-makroerne og et rent statusfelt kl. 9:41). Apple skalerer selv ned til de mindre størrelser.

1. Makrolisten med mapper
2. Editoren (hvornår: tid, ugedage, næste afsendelse)
3. Beskeden med variabler og AI-variation
4. Widgets
5. Guiden til Genveje

## 6. Noter til Apples gennemgang (App Review Information)

> TextFox never sends texts in the background. At a scheduled time it shows a local notification; tapping Send opens the system message composer pre-filled, and the user sends it. For fully automatic sending the app offers App Intents ("Get Due TextFox Macros", "Get TextFox Message", "Get TextFox Recipients", "Get TextFox Auto-Reply"). The user adds a shared shortcut, "TextFox Send", whose iOS 27 notification automation runs these with Shortcuts' own Send Message action when a TextFox notification arrives; the user switches the automation on themselves (Settings → Send automatically explains it). On older iOS versions the same shortcut runs from Time of Day automations the user creates.
>
> To test: open the app, go through the short intro, tap "Start 30-day free trial", then long-press any macro → Send now. No login is needed. AI features need a free Google Gemini API key and are optional.

Kontakt: Lars Sohl, hello@textfox.app.

## 7. Bygge og sende ind

1. Sæt iPhonen til (Udviklertilstand til) og kør `ios/scripts/bootstrap.sh --device` én gang. Så opretter Xcode App ID'erne, App Group'en og certifikaterne i din konto.
2. Xcode → Product → Archive (vælg "Any iOS Device"). Organizer → Distribute App → App Store Connect → Upload.
3. I App Store Connect: TestFlight → tilføj dig selv som tester, og prøv appen på telefonen.
4. Når alt virker: udfyld afsnit 3–6, vælg buildet under version 1.0, og tryk **Submit for Review**.
