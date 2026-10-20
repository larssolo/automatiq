# Play Console — udfyldningsguide for Automatiq

Udkast til alt, der skal udfyldes i Play Console for `app.kodekid.automatiq`. Tekster i kodeblokke er klar til at kopiere ind (engelsk, som appen). Punkter markeret **⚠ Beslut** kræver dit valg, før de sendes ind.

> Google ændrer formularerne løbende. Brug teksterne som udgangspunkt, og læs feltet i konsollen, før du indsætter.

---

## 0. Før du starter

- [ ] **Privatlivspolitik online.** Online på `https://larssolo.github.io/automatiq-privacy/` (separat offentligt repo `larssolo/automatiq-privacy`, så dette repo kan være privat). Ret politikken i begge repoer, hvis den ændres.
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
Automate texts, sound mode, reminders and webhooks — right on your phone.
```

**Full description** (maks 4000 tegn):
```
Automatiq puts your phone on autopilot.

Set up a macro once — when it should run and what it should do — and Automatiq takes care of it. Everything runs on your phone: no account, no server, no ads.

FOUR WAYS TO TRIGGER A MACRO
• Schedule — at 08:30 on weekdays, every second week, on 1–2 random days, until a date you choose
• One tap — from the list or a home-screen widget
• Incoming text — when a message from a chosen sender or with a keyword arrives
• Missed call — when you can't pick up

FOUR THINGS A MACRO CAN DO
• Send a text — to one or many recipients, or reply to the sender or caller
• Change sound mode — Normal, Vibrate, Silent or Do Not Disturb
• Show a reminder — a notification with your own text
• Call a web address — trigger your smart home (Home Assistant, IFTTT and more)

MADE FOR REAL LIFE
• Multiple recipients per macro, with contact names
• Message variables like {dato}, {tid}, {ugedag} and {modtager}
• Random send time and random days, so it never feels robotic
• Folders with a master switch
• Quiet hours for auto-replies
• Delivery status and a full log of everything that ran
• Export and import backups

OPTIONAL AI (GOOGLE GEMINI)
Bring your own free Gemini API key and let AI write auto-replies, fresh variations of your scheduled texts, or suggestions for a new message. Approve each AI text before it's sent — or ask for a new suggestion right from the notification. AI is off until you add a key and agree to send text to Google, and you can report any AI text that's wrong or offensive right in the app.

SET UP IN A MINUTE
A guided setup walks you through every permission Android (and Xiaomi, Redmi and POCO phones) need for reliable background automation — one tap, and it tells you exactly what to switch on.

PRIVACY
Your macros, contacts and messages stay on your phone. Data only leaves it when you use an AI feature (sent directly to Google with your own key), report an AI text, or when a macro calls a web address you set up yourself.
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

**Webhook-handlingen:** sender en forespørgsel til en adresse, brugeren selv indtaster, med en tekst brugeren selv skriver. Det er brugerstyret overførsel til brugerens egen tjeneste og ikke indsamling hos dig. Hvis du vil være på den sikre side, kan du angive *App activity → Other user-generated content* som "delt, valgfri, brugerstyret".

**Ingen** data behandles ephemeralt hos dig; rapporter gemmes. Gemini-data "deles" fordi teksten sendes til en tredjepart (Google).

### 3.7 Government apps / Financial features / Health
Nej til alle.

---

## 4. Tilladelseserklæringer (Permissions declaration form)

### 4.1 SMS og opkaldslog (`SEND_SMS`, `RECEIVE_SMS`, `READ_CALL_LOG`)

Appen er ikke standard-SMS-app. Vælg undtagelsen **Device automation**. Googles ordlyd: *"apps that enable the user to automate repetitive actions across multiple areas of the OS, based on one or more conditions (triggers) set by the user"* — teksten nedenfor er skrevet til netop den formulering.

**Core functionality:**
```
Automatiq is a device-automation app. The user builds "macros": a trigger they choose and an action the phone then performs automatically.

Triggers: a time schedule (alarms), a one-tap/home-screen widget, an incoming SMS from a chosen sender or containing a keyword, or a missed or declined call.
Actions: change the phone's sound mode or Do Not Disturb, show a reminder notification, call a web address (for example to control smart-home devices), or send an SMS.

Triggers and actions therefore span several areas of the OS — alarms, telephony, messaging, audio and notification policy, notifications and the network. SMS and Call Log access is only used for the macros the user creates:

- SEND_SMS: the "Send SMS" action sends the user's own message to recipients the user picked, or replies to the sender/caller of a trigger the user set up.
- RECEIVE_SMS: the "incoming SMS" trigger detects messages that match the user's sender/keyword condition so the macro can run.
- READ_CALL_LOG: the "missed call" trigger. On Android 9+ the caller's number in the phone-state broadcast is only provided with this permission; it is used only to reply to that caller. The call log is not read, stored or uploaded.

SMS and call data is processed on the device and never uploaded, sold or shared — with one user-controlled exception: if the user turns on the optional AI reply feature and explicitly consents in the app, the text of the incoming message is sent to Google's Gemini API with the user's own API key to write the reply. Without these permissions the SMS and call triggers/actions cannot work; Android offers no alternative API for automated SMS.
```
**Video** (≤ 30 sek, YouTube/Drive-link): `declaration-device-automation.mp4` (se `store/README.md`) viser flere handlinger + SMS-automatisering. Manus:
1. Makro-listen med forskellige handlinger (lyd, påmindelse, webhook, SMS).
2. "Quiet mode" køres → telefonen skifter til lydløs (ikon i statuslinjen).
3. "Pills" køres → påmindelsen dukker op.
4. En SMS kommer ind → auto-svaret sendes, og det ses i Log.

### 4.2 Foreground service
**Ikke relevant for Play-versionen.** Opladning/Bluetooth/Wi-Fi-triggerne (og tjenesten bag dem) er kun med i den delbare APK. Play-bundlen har ingen foreground service-type at erklære.

### 4.3 Exact alarms (`SCHEDULE_EXACT_ALARM`)
Hvis konsollen spørger:
```
Scheduled macros send a user-set message at an exact user-set time (e.g. 08:30). The user grants this in Android's "Alarms & reminders" setting from the in-app setup; without it the app falls back to inexact alarms.
```

### 4.4 Batterioptimering (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
Ingen formular, men brugen skal holde politikken (Task automation app). Begrundelse ved review:
```
Core function is running user-scheduled automated actions (sound mode, reminders, webhooks and SMS at user-set times and on user-set conditions). Doze and App Standby would delay or drop them.
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
