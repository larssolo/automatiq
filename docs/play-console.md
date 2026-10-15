# Play Console — udfyldningsguide for Automatiq

Udkast til alt, der skal udfyldes i Play Console for `app.kodekid.automatiq`. Tekster i kodeblokke er klar til at kopiere ind (engelsk, som appen). Punkter markeret **⚠ Beslut** kræver dit valg, før de sendes ind.

> Google ændrer formularerne løbende. Brug teksterne som udgangspunkt, og læs feltet i konsollen, før du indsætter.

---

## 0. Før du starter

- [ ] **Privatlivspolitik online.** `docs/privacy.md` udgives via GitHub Pages. URL: `https://larssolo.github.io/automatiq/privacy` (når Pages er slået til på `main` / `docs`).
- [x] Pladsholderne i `docs/privacy.md` er udfyldt (Lars Sohl, founder of kodekid.app · hello@larssohl.dk).
- [x] Rapporter slettes senest efter 12 måneder (besluttet). Husk oprydning i Neon: `DELETE FROM ai_reports WHERE created_at < now() - interval '12 months'`.
- [ ] Upload-nøglen er sikkerhedskopieret (`~/.android/automatiq-upload.jks` + `keystore.properties`).
- [ ] Byg: `./gradlew bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab`.
- [ ] Slå **Play App Signing** til, når du opretter appen (standard).

---

## 1. Opret appen

| Felt | Værdi |
|---|---|
| App name | `Automatiq — SMS Automation` (maks 30 tegn) |
| Default language | English (United States) |
| App or game | App |
| Free or paid | Free |

---

## 2. Butiksside (Main store listing)

**Short description** (maks 80 tegn):
```
Schedule, auto-reply and trigger SMS — on your phone, with optional AI.
```

**Full description** (maks 4000 tegn):
```
Automatiq puts your text messages on autopilot.

Set up a macro once — who it goes to, what it says and when — and Automatiq sends it for you. Everything runs on your phone: no account, no server, no ads.

FIVE WAYS TO FIRE A MESSAGE
• Schedule — at 08:30 on weekdays, every second week, until a date you choose
• One tap — from the list or a home-screen widget
• Auto-reply — answer incoming texts that match a sender or keyword
• Missed call — text people back when you can't pick up
• Device — when the charger is plugged in, or a Bluetooth device or Wi-Fi network connects

MADE FOR REAL LIFE
• Multiple recipients per macro, with contact names
• Message variables like {dato}, {tid}, {ugedag} and {modtager}
• Folders with a master switch
• Quiet hours for auto-replies
• Delivery status and a full log of everything sent
• Export and import backups

OPTIONAL AI (GOOGLE GEMINI)
Bring your own free Gemini API key and let AI write auto-replies, fresh variations of your scheduled texts, or suggestions for a new message. You choose per macro whether AI texts are sent automatically or wait for your approval. AI is off until you add a key and agree to send text to Google — and you can report any AI text that's wrong or offensive right in the app.

SET UP IN A MINUTE
A guided setup walks you through every permission Android (and Xiaomi, Redmi and POCO phones) need for reliable background sending — one tap, and it tells you exactly what to switch on.

PRIVACY
Your macros, contacts and messages stay on your phone. Text only leaves it when you use an AI feature (sent directly to Google with your own key) or report an AI text.
```

**Grafik** (klar i `store/`, se `store/README.md`):
- [x] App-ikon 512×512: `store/icon-512.png`
- [x] Feature graphic 1024×500: `store/feature-1024x500.png`
- [x] 5 telefon-skærmbilleder 1080×1920: `store/screenshots/01-…05-*.jpg`

**Kategori:** Tools · **Kontakt-e-mail:** `hello@larssohl.dk` · **Privatlivspolitik:** URL fra afsnit 0.

---

## 3. App content

### 3.1 Privacy policy
URL fra afsnit 0.

### 3.2 Ads
`No, my app does not contain ads.`

### 3.3 App access
`All functionality is available without special access.` (ingen login). AI kræver brugerens egen nøgle — nævn det:
```
No login is required. AI features are optional and use the user's own free Google Gemini API key (created at aistudio.google.com/apikey); all other features work without it.
```

### 3.4 Content rating (IARC-spørgeskema)
Kategori **Utility / Productivity**. Svar *nej* til vold, sex, sprog, stoffer, gambling. *Brugerinteraktion:* appen sender SMS'er, men har ingen deling/chat mellem brugere i appen. Forventet resultat: Everyone / PEGI 3.

### 3.5 Target audience
Vælg **18+** (eller 13+). Ikke rettet mod børn.

### 3.6 Data safety

**Indsamler/deler appen data?** Ja.
**Krypteres al data under overførsel?** Ja (HTTPS).
**Kan brugere anmode om sletning?** Ja (via kontakt-e-mail, se politikken afsnit 6).

| Datatype | Indsamlet | Delt | Valgfri | Formål | Kommentar |
|---|---|---|---|---|---|
| **Messages → SMS or MMS** | Ja | Ja (Google, Gemini API) | Ja | App functionality | Kun ved AI-svar: teksten i den indgående SMS sendes til Gemini med brugerens egen nøgle |
| **App activity → Other user-generated content** | Ja | Nej | Ja | App functionality *(forbedring af AI-filtre)* | AI-rapporter: den rapporterede AI-tekst, årsag og valgfri note |
| Location | Nej | — | — | — | Play-versionen har ingen lokation |
| Contacts | Nej | — | — | — | Læses kun på telefonen |
| Phone numbers / call logs | Nej | — | — | — | Bruges kun på telefonen |

**Ingen** data behandles ephemeralt hos dig; rapporter gemmes. Gemini-data "deles" fordi teksten sendes til en tredjepart (Google).

### 3.7 Government apps / Financial features / Health
Nej til alle.

---

## 4. Tilladelseserklæringer (Permissions declaration form)

### 4.1 SMS og opkaldslog (`SEND_SMS`, `RECEIVE_SMS`, `READ_CALL_LOG`)

Appen er ikke standard-SMS-app. Vælg undtagelsen **Device automation**.

**Core functionality:**
```
Automatiq is a task-automation app whose core purpose is sending SMS messages automatically based on conditions the user sets up in advance ("macros"): a schedule, an incoming SMS that matches a sender/keyword (auto-reply), a missed or declined call (text the caller back), or a device-state change (charger, Bluetooth, Wi-Fi).

- SEND_SMS: sends the user's own macro messages to recipients the user chose.
- RECEIVE_SMS: detects incoming messages that match an auto-reply macro, so the app can reply to the sender.
- READ_CALL_LOG: on Android 9+ the caller's number in the phone-state broadcast is only provided with this permission; it is needed to text back the caller of a missed call. The call log itself is not read or stored.

Every message is configured explicitly by the user. Without these permissions the app has no function.
```
**Video** (≤ 30 sek, YouTube/Drive-link): optaget som `declaration-sms-device-automation.mp4` (se `store/README.md`). Manus:
1. Åbn appen → opret makro "Auto-reply" med nøgleord "status".
2. Send en SMS med "status" fra en anden telefon → svaret ankommer.
3. Vis en planlagt makro (08:30, hverdage) og "Missed call"-makro.
4. Vis tilladelsesdialogen i Phone setup.

### 4.2 Foreground service — `specialUse`

```
TriggerMonitorService runs only while the user has at least one enabled device-state macro (charging, Bluetooth or Wi-Fi trigger). It listens for these system state changes so the macro fires immediately, which can't be done reliably with WorkManager or implicit broadcasts (most are no longer delivered to manifest receivers). A persistent notification shows while it runs, and it stops as soon as no such macro is enabled.
```
Video: `declaration-foreground-service.mp4` (se `store/README.md`). Viser tjenestens notifikation, oplader sættes i → makroen affyres.

### 4.3 Exact alarms (`SCHEDULE_EXACT_ALARM`)
Hvis konsollen spørger:
```
Scheduled macros send a user-set message at an exact user-set time (e.g. 08:30). The user grants this in Android's "Alarms & reminders" setting from the in-app setup; without it the app falls back to inexact alarms.
```

### 4.4 Batterioptimering (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
Ingen formular, men brugen skal holde politikken (Task automation app). Begrundelse ved review:
```
Core function is scheduling automated actions (sending SMS at user-set times and on user-set conditions). Doze and App Standby would delay or drop these sends.
```

---

## 5. Test før produktion (ny personlig konto)

- [ ] Opret **Closed testing**-spor og upload `.aab`.
- [ ] Tilføj mindst **12 testere** (e-mail-liste eller Google-gruppe), som skal være tilmeldt **14 dage i træk**.
- [ ] Testerne installerer via opt-in-linket og bruger appen.
- [ ] Efter 14 dage: ansøg om produktionsadgang (spørgsmål om testen og appen).

## 6. Første produktionsudgivelse

- [ ] Release name: `1.3`
- [ ] Release notes:
```
First Play Store release of Automatiq.
• Scheduled, one-tap, auto-reply, missed-call and device-state SMS macros
• Guided phone setup for reliable background sending
• Optional AI (Google Gemini) with approval mode and in-app reporting
```
