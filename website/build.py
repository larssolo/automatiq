#!/usr/bin/env python3
"""Builds the static TextFox website into ./public.

    python3 website/build.py          (needs: pip install markdown)

Pages live in src/pages/*.html with a JSON header comment. Shared head, nav,
footer, FAQ data and schema.org markup are generated here, so every page
stays consistent. The output in public/ is committed and served as-is by Vercel.
"""
import datetime, hashlib, html, json, pathlib, re, urllib.parse

import markdown

ROOT = pathlib.Path(__file__).resolve().parent
PUB = ROOT / "public"
SITE = "https://www.textfox.app"

# ---- one place to change launch details ------------------------------------
PLAY_URL = "https://play.google.com/apps/testing/app.kodekid.automatiq"  # swap for the production listing at launch
CTA_NOTE = "Early access on Google Play · 30 days free · Android 8.0+"
CTA_NOTE_DA = "Tidlig adgang på Google Play · 30 dage gratis · Android 8.0+"
CONTACT = "hello@larssohl.dk"
PUBLISHER = "kodekid.app"
TODAY = datetime.date.today().isoformat()

# ---- languages -------------------------------------------------------------
# English lives at the root, Danish under /da. PAIRS links each page to its translation (hreflang + language switch).
PAIRS = [("/", "/da"), ("/scheduled-text-messages", "/da/planlagte-sms"), ("/auto-reply-sms", "/da/autosvar-sms"),
         ("/missed-call-text-back", "/da/sms-ved-ubesvaret-opkald"), ("/one-tap-text-widget", "/da/sms-med-et-tryk"), ("/about", "/da/om"), ("/creators", "/da/influencere"), ("/sitemap", "/da/sitemap")]


def counterpart(path):
    for en, da in PAIRS:
        if path == en: return "da", da
        if path == da: return "en", en
    return None


def abs_url(path):
    return SITE + ("/" if path == "/" else path)


T = {
    "en": {"locale": "en_US", "home": "/", "skip": "Skip to content", "home_aria": "TextFox home",
           "nav": ["A day", "Features", "AI", "Privacy", "FAQ"], "get_app": "Get the app", "play_small": "Get it on",
           "cta_note": CTA_NOTE,
           "f_tag": "Scheduled texts, auto-replies and missed-call text-back for Android. Set it up once. It just runs.",
           "f_features": "Features", "f_contact": "Contact", "about": "About the maker", "privacy": "Privacy policy", "made_by": "Made by",
           "no_track": "No cookies. No tracking.", "related_h": "More of what TextFox does",
           "pages": {"sched": ("/scheduled-text-messages", "Scheduled texts"), "auto": ("/auto-reply-sms", "Auto-reply"), "missed": ("/missed-call-text-back", "Missed-call text-back"),
                     "tap": ("/one-tap-text-widget", "One-tap widget"), "ai": ("/#ai", "AI variations"), "about": ("/about", "About the maker"), "faq": ("/#faq", "FAQ"), "creators": ("/creators", "For creators")},
           "privacy_href": "/privacy", "crumb_root": "TextFox",
           "share_text": "TextFox sends your texts for you: scheduled SMS, auto-replies and missed-call text-back for Android.",
           "share_subject": "Have a look at TextFox", "share_h": "Know someone who's always running late?",
           "share_p": "Send them TextFox. The first 30 days are free, and it might save them a lot of typing.", "share": "Share", "email": "Email", "copy": "Copy link",
           "final_h": "Set it up once.<br>It just runs.", "final_p": "For Android 8.0 and newer. Free for 30 days, then a one-time $7. Runs on your phone: no account, no ads, no tracking.",
           "og_alt": "TextFox: your phone texts for you. Scheduled texts, auto-replies and missed-call text-back for Android.",
           "switch": ("DA", "Dansk", "da")},
    "da": {"locale": "da_DK", "home": "/da", "skip": "Gå til indhold", "home_aria": "TextFox forside",
           "nav": ["En dag", "Funktioner", "AI", "Privatliv", "FAQ"], "get_app": "Hent appen", "play_small": "Hent den på",
           "cta_note": CTA_NOTE_DA,
           "f_tag": "Planlagte sms'er, autosvar og sms ved ubesvaret opkald til Android. Sæt det op én gang. Så kører det bare.",
           "f_features": "Funktioner", "f_contact": "Kontakt", "about": "Om skaberen", "privacy": "Privatlivspolitik (engelsk)", "made_by": "Lavet af",
           "no_track": "Ingen cookies. Ingen sporing.", "related_h": "Mere af det, TextFox kan",
           "pages": {"sched": ("/da/planlagte-sms", "Planlagte sms'er"), "auto": ("/da/autosvar-sms", "Autosvar"), "missed": ("/da/sms-ved-ubesvaret-opkald", "Sms ved ubesvaret opkald"),
                     "tap": ("/da/sms-med-et-tryk", "Sms med ét tryk"), "ai": ("/da#ai", "AI-varianter"), "about": ("/da/om", "Om skaberen"), "faq": ("/da#faq", "FAQ"), "creators": ("/da/influencere", "Til influencere")},
           "privacy_href": "/privacy", "crumb_root": "TextFox",
           "share_text": "TextFox sender dine sms'er for dig: planlagte sms'er, autosvar og sms ved ubesvaret opkald til Android.",
           "share_subject": "Kig lige på TextFox", "share_h": "Kender du en, der altid kommer for sent?",
           "share_p": "Send dem TextFox. De første 30 dage er gratis, og den kan spare dem for en masse tasten.", "share": "Del", "email": "E-mail", "copy": "Kopiér link",
           "final_h": "Sæt det op én gang.<br>Så kører det bare.", "final_p": "Til Android 8.0 og nyere. Gratis i 30 dage, derefter et engangskøb på 7 USD. Kører på din telefon: ingen konto, ingen reklamer, ingen sporing.",
           "og_alt": "TextFox: din telefon sender sms'er for dig. Planlagte sms'er, autosvar og sms ved ubesvaret opkald til Android.",
           "switch": ("EN", "English", "en")},
}

FAQ = {
    "home": [
        ("What is TextFox?", "TextFox is an Android app that sends text messages (SMS) for you automatically. You create a macro, which is a trigger plus a message, and TextFox sends it on a schedule, as a reply to an incoming text, after a missed call, or when you tap a home-screen widget."),
        ("What does TextFox cost?", "The first 30 days are free, with every feature. After that, a one-time purchase of $7 on Google Play keeps your macros running. There is no subscription and no ads, and the purchase follows your Google account to a new phone. The optional AI features use your own free Google Gemini API key. Texts are sent through your normal mobile plan, so your carrier's usual SMS rates apply."),
        ("Do I need an account?", "No. There is no sign-up and no login. Install the app, allow the permissions the guided setup asks for, and create your first macro."),
        ("Does TextFox work on iPhone?", "No. TextFox is for Android 8.0 and newer. iPhones don't allow apps to send text messages automatically in the background."),
        ("Can TextFox reply to WhatsApp messages?", "No. TextFox sends and replies to regular SMS text messages only."),
        ("Will scheduled texts still go out if my phone restarts or is asleep?", "Yes. TextFox uses Android's exact alarms with a backup scheduler, and re-arms every macro after a restart or an app update. The guided setup asks you to allow exact alarms and to exclude TextFox from battery optimisation, which Android needs for reliable background sending."),
        ("What does the AI do, and what is sent to Google?", "AI is optional and off until you add your own Gemini key. It can rewrite scheduled texts so they never read the same twice, write auto-replies and suggest new messages. Only the message text and your optional instruction are sent, directly from your phone to Google's Gemini API. Phone numbers and contact names are not sent."),
        ("Can one macro text several people?", "Yes. A macro can have many recipients, and each copy can include that person's contact name."),
    ],
    "scheduled": [
        ("Can I schedule a text for one specific date?", "Yes. Choose a one-time schedule and pick the date and time. Recurring schedules can also have an end date."),
        ("Will a scheduled text go out if my phone restarts?", "Yes. TextFox re-arms every schedule after a restart or app update, and a backup scheduler catches up if Android delayed an alarm."),
        ("Can I send the same scheduled text to several people?", "Yes. Add as many recipients as you like. Each copy can include that person's name."),
        ("Does a scheduled text come from my own number?", "Yes. TextFox sends a normal SMS from your phone and your number, using your mobile plan."),
    ],
    "autoreply": [
        ("Can TextFox auto-reply only to certain people?", "Yes. Each auto-reply macro can be limited to chosen senders, to texts containing a keyword, or both."),
        ("Can I stop auto-replies at night?", "Yes. Set quiet hours. Texts that arrive in that window are answered when it ends, with one reply per person to their latest message."),
        ("Does it reply to WhatsApp or Messenger?", "No. TextFox replies to regular SMS text messages only."),
        ("Can AI write the auto-reply?", "Yes, if you add your own free Google Gemini key. You can approve each reply in a notification before it is sent, or let it send automatically and get notified."),
    ],
    "tap": [
        ("Does the widget send without opening the app?", "Yes. One tap on the widget sends the macro's text straight away. You don't need to open TextFox."),
        ("Can one widget text several people?", "Yes. The widget sends the macro to every recipient on its list, and each copy can include that person's name."),
        ("How do I know the text was sent?", "The widget's subtitle shows the last send, for example \"Last: 14:32 ✓\", and the log in the app shows delivery status."),
        ("Can I have several widgets?", "Yes. Add one widget per macro and place as many as you like on your home screens."),
    ],
    "missed": [
        ("Does it work when I decline a call?", "Yes. Both calls that ring out and calls you decline trigger the text-back."),
        ("Can I limit it to certain callers?", "Yes. An optional caller filter limits the text-back to the numbers you choose."),
        ("Which permissions does it need?", "Phone state and call log access, to notice a missed call, and SMS, to send the text. Both are used only on your phone and are never uploaded."),
        ("How fast is the text sent?", "Usually a few seconds after the call ends. Every send is logged and marked Delivered when your carrier confirms it."),
    ],
}

FAQ_DA = {
    "home": [
        ("Hvad er TextFox?", "TextFox er en Android-app, der sender sms'er for dig automatisk. Du laver en makro, som er en udløser plus en besked, og TextFox sender den på et fast tidspunkt, som svar på en indgående sms, efter et ubesvaret opkald, eller når du trykker på en widget på hjemmeskærmen."),
        ("Hvad koster TextFox?", "De første 30 dage er gratis, med alle funktioner. Derefter holder et engangskøb på 7 USD på Google Play dine makroer kørende. Der er intet abonnement og ingen reklamer, og købet følger din Google-konto til en ny telefon. De valgfrie AI-funktioner bruger din egen gratis Google Gemini-nøgle. Sms'erne sendes via dit almindelige mobilabonnement, så dit teleselskabs normale sms-priser gælder."),
        ("Skal jeg oprette en konto?", "Nej. Der er ingen tilmelding og intet login. Installér appen, giv de tilladelser, den guidede opsætning beder om, og lav din første makro."),
        ("Virker TextFox på iPhone?", "Nej. TextFox er til Android 8.0 og nyere. iPhone tillader ikke apps at sende sms'er automatisk i baggrunden."),
        ("Kan TextFox svare på WhatsApp-beskeder?", "Nej. TextFox sender og svarer kun på almindelige sms'er."),
        ("Bliver planlagte sms'er sendt, selv om telefonen genstarter eller sover?", "Ja. TextFox bruger Androids præcise alarmer med en reserveplanlægger og genaktiverer alle makroer efter en genstart eller en opdatering. Den guidede opsætning beder dig tillade præcise alarmer og undtage TextFox fra batterioptimering, hvilket Android kræver for at kunne sende pålideligt i baggrunden."),
        ("Hvad gør AI'en, og hvad sendes til Google?", "AI er valgfrit og slået fra, indtil du tilføjer din egen Gemini-nøgle. Den kan omskrive planlagte sms'er, så de aldrig lyder ens, skrive autosvar og foreslå nye beskeder. Kun beskedteksten og din eventuelle instruks sendes, direkte fra din telefon til Googles Gemini API. Telefonnumre og kontaktnavne sendes ikke."),
        ("Kan én makro sende til flere personer?", "Ja. En makro kan have mange modtagere, og hver kopi kan indeholde personens kontaktnavn."),
    ],
    "scheduled": [
        ("Kan jeg planlægge en sms til en bestemt dato?", "Ja. Vælg en engangsplan, og vælg dato og klokkeslæt. Gentagne planer kan også have en slutdato."),
        ("Bliver en planlagt sms sendt, hvis telefonen genstarter?", "Ja. TextFox genaktiverer alle planer efter en genstart eller appopdatering, og en reserveplanlægger indhenter, hvis Android har forsinket en alarm."),
        ("Kan jeg sende den samme planlagte sms til flere personer?", "Ja. Tilføj så mange modtagere, du vil. Hver kopi kan indeholde personens navn."),
        ("Kommer en planlagt sms fra mit eget nummer?", "Ja. TextFox sender en helt almindelig sms fra din telefon og dit nummer via dit mobilabonnement."),
    ],
    "autoreply": [
        ("Kan TextFox kun autosvare bestemte personer?", "Ja. Hver autosvar-makro kan begrænses til udvalgte afsendere, til sms'er med et bestemt ord, eller begge dele."),
        ("Kan jeg stoppe autosvar om natten?", "Ja. Indstil stille timer. Sms'er, der kommer i det tidsrum, besvares, når det slutter, med ét svar pr. person til deres seneste besked."),
        ("Svarer den på WhatsApp eller Messenger?", "Nej. TextFox svarer kun på almindelige sms'er."),
        ("Kan AI skrive autosvaret?", "Ja, hvis du tilføjer din egen gratis Google Gemini-nøgle. Du kan godkende hvert svar i en notifikation, før det sendes, eller lade det sende automatisk og få besked bagefter."),
    ],
    "tap": [
        ("Sender widgetten uden at åbne appen?", "Ja. Ét tryk på widgetten sender makroens sms med det samme. Du behøver ikke åbne TextFox."),
        ("Kan én widget sende til flere personer?", "Ja. Widgetten sender makroen til alle modtagere på listen, og hver kopi kan indeholde personens navn."),
        ("Hvordan ved jeg, at sms'en er sendt?", "Widgettens undertekst viser seneste afsendelse, fx \"Last: 14:32 ✓\", og loggen i appen viser leveringsstatus."),
        ("Kan jeg have flere widgets?", "Ja. Tilføj én widget pr. makro, og placér så mange, du vil, på dine hjemmeskærme."),
    ],
    "missed": [
        ("Virker det, når jeg afviser et opkald?", "Ja. Både opkald, der ringer ud, og opkald, du afviser, udløser sms'en."),
        ("Kan jeg begrænse det til bestemte opkaldere?", "Ja. Et valgfrit filter begrænser sms'en til de numre, du vælger."),
        ("Hvilke tilladelser kræver det?", "Adgang til telefonstatus og opkaldslog for at opdage et ubesvaret opkald, og sms for at sende beskeden. Begge dele bruges kun på din telefon og uploades aldrig."),
        ("Hvor hurtigt sendes sms'en?", "Normalt få sekunder efter opkaldet. Hver afsendelse logges og markeres som leveret, når dit teleselskab bekræfter det."),
    ],
}

ICONS = """<svg width="0" height="0" style="position:absolute" aria-hidden="true" focusable="false">
<symbol id="fox" viewBox="0 0 354.18 302.05"><path d="M3.03,167.98l108.55,18.34c18.05,15.38,83.78,26.23,102.7,10.13l136.86-28.13c1.06-.91,4.45-1.28,2.38-4.76l-16.81-47.49L327.15,0l-99.48,74.39h-100.38S27.76,0,27.76,0l-8.57,116.06L.88,163.68c-.59.82-1,1.64-.84,2.21.25.92,1.26,1.41,3,2.1Z" fill="#121815"/><path d="M351.14,168.32c-2.58-.26-5.33.02-8.35,1.16l-87.63,24.45-77.62,102.84-77.78-102.9-88.64-24.75c-3.04-.85-5.58-1.5-8.09-1.14-1.73-.69-2.74-1.18-3-2.1-.16-.57.25-1.38.84-2.21l18.32-47.61L27.76,0l99.53,74.39h100.38S327.15,0,327.15,0l9.56,116.08,16.81,47.49c2.07,3.48-1.32,3.86-2.38,4.76Z" fill="currentColor"/><path d="M351.14,168.32l-144.48,122.95c-18.92,16.1-43.45,13.15-61.5-2.22L3.03,167.98c2.51-.36,5.05.29,8.09,1.14l88.64,24.75,77.78,102.9,77.62-102.84,87.63-24.45c3.03-1.14,5.77-1.43,8.35-1.16Z" fill="#E5F3EA"/><ellipse class="eye" cx="121.96" cy="149.52" rx="19.4" ry="19.4" fill="#121815"/><ellipse class="eye" cx="177.51" cy="149.52" rx="19.4" ry="19.4" fill="#121815"/><ellipse class="eye" cx="233.04" cy="149.52" rx="19.4" ry="19.4" fill="#121815"/></symbol>
<symbol id="i-clock" viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/></symbol>
<symbol id="i-bell" viewBox="0 0 24 24"><path d="M6.5 16v-5a5.5 5.5 0 0 1 11 0v5l1.5 2h-14z"/><path d="M10 20.5a2 2 0 0 0 4 0"/></symbol>
<symbol id="i-reply" viewBox="0 0 24 24"><path d="M10 7 5 12l5 5"/><path d="M5 12h9a5 5 0 0 1 5 5v1"/></symbol>
<symbol id="i-missed" viewBox="0 0 24 24"><path d="M3.5 16.5c4.7-4 12.3-4 17 0l-1.8 2.7-3.2-1.3v-2.5a10 10 0 0 0-7 0v2.5l-3.2 1.3z"/><path d="m6 5 5 5 4-4m0 0h-3m3 0v3"/></symbol>
<symbol id="i-tap" viewBox="0 0 24 24"><path d="M9.5 12V5.8a1.6 1.6 0 0 1 3.2 0V11"/><path d="M12.7 10.6a1.6 1.6 0 0 1 3.2 0v1.2a1.6 1.6 0 0 1 3.2 0V15a6 6 0 0 1-6 6h-.6a6 6 0 0 1-5-2.7L4.6 14.6a1.6 1.6 0 0 1 2.6-1.9l2.3 2.6"/></symbol>
<symbol id="i-moon" viewBox="0 0 24 24"><path d="M19.5 14.5A7.5 7.5 0 1 1 9.5 4.5a6 6 0 0 0 10 10z"/></symbol>
<symbol id="i-sound" viewBox="0 0 24 24"><path d="M4 9.5h3.5L12 5.5v13l-4.5-4H4z"/><path d="M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"/></symbol>
<symbol id="i-link" viewBox="0 0 24 24"><circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.4 2.4 3.5 5.3 3.5 8.5s-1.1 6.1-3.5 8.5c-2.4-2.4-3.5-5.3-3.5-8.5s1.1-6.1 3.5-8.5z"/></symbol>
<symbol id="i-check" viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7.5"/></symbol>
<symbol id="i-spark" viewBox="0 0 24 24"><path d="M12 3.5 13.8 9l5.7 1.8-5.7 1.9L12 18.5l-1.8-5.8-5.7-1.9L10.2 9z"/></symbol>
</svg>"""

PLAY_SVG = '<svg width="18" height="20" viewBox="0 0 16 18" aria-hidden="true"><path d="M1 1.2v15.6c0 .5.5.8.9.6L15 9.6a.7.7 0 0 0 0-1.2L1.9.6C1.5.4 1 .7 1 1.2Z" fill="currentColor"/></svg>'
def play(lang="en"):
    return f'<a class="btn btn-g play-btn" href="{PLAY_URL}" rel="noopener">{PLAY_SVG}<span><small>{T[lang]["play_small"]}</small>Google Play</span></a>'


PLAY = play("en")


def ver(rel):
    return hashlib.sha1((PUB / rel.lstrip("/")).read_bytes()).hexdigest()[:8]


def faqs(lang):
    return FAQ_DA if lang == "da" else FAQ


def faq_html(key, lang="en"):
    items = "".join(f'<details><summary>{html.escape(q)}</summary><div><p>{html.escape(a)}</p></div></details>' for q, a in faqs(lang)[key])
    return f'<div class="faq">{items}</div>'


def faq_schema(key, url, lang="en"):
    return {"@type": "FAQPage", "@id": url + "#faq", "inLanguage": lang, "mainEntity": [
        {"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in faqs(lang)[key]]}


PERSON = {"@type": "Person", "@id": SITE + "/about#lars", "name": "Lars Sohl", "url": SITE + "/about",
          "jobTitle": "Graphic designer, photographer and creative coder", "email": CONTACT,
          "description": "Danish graphic designer, photographer and creative coder. Founder of kodekid.app, klokly.app and larssohl.dk, maker of TextFox.",
          "homeLocation": {"@type": "Country", "name": "Denmark"}, "areaServed": "Europe",
          "worksFor": {"@type": "Organization", "name": "Brandsurface", "url": "https://www.brandsurface.dk"},
          "knowsAbout": ["Graphic design", "Photography", "Creative coding", "Android apps", "Web development", "Digital solutions"],
          "sameAs": ["https://www.larssohl.dk", "https://www.kodekid.app", "https://www.klokly.app"]}
ORG = {"@type": "Organization", "@id": SITE + "/#org", "name": "TextFox", "url": SITE + "/",
       "logo": SITE + "/icon-512.png", "email": CONTACT, "founder": {"@id": SITE + "/about#lars"},
       "parentOrganization": {"@type": "Organization", "name": PUBLISHER, "url": "https://www.kodekid.app", "founder": {"@id": SITE + "/about#lars"}}}
APP = {"@type": "MobileApplication", "@id": SITE + "/#app", "name": "TextFox", "url": SITE + "/",
       "description": "TextFox is an Android app that sends text messages automatically: scheduled SMS, auto-replies to incoming texts, missed-call text-back and one-tap widget messages, with optional AI-written variations using Google Gemini. Runs on the phone, with no account and no ads. Free for 30 days, then a one-time purchase.",
       "operatingSystem": "Android 8.0 or later", "applicationCategory": "UtilitiesApplication", "applicationSubCategory": "SMS automation",
       "installUrl": PLAY_URL, "downloadUrl": PLAY_URL, "image": SITE + "/icon-512.png",
       "screenshot": [SITE + f"/media/screen-{s}.webp" for s in ("autopilot", "macros", "ai-variation", "phone-setup", "log")],
       "offers": {"@type": "Offer", "price": "7.00", "priceCurrency": "USD", "description": "30-day free trial, then a one-time in-app purchase"},
       "featureList": ["Scheduled text messages by time, weekday, every 1–4 weeks, one date or random days", "Auto-reply to incoming SMS by sender or keyword, with quiet hours",
                       "Missed-call and declined-call text-back", "One-tap home-screen widgets", "Multiple recipients with personalised names",
                       "Optional AI variations and AI replies with your own Google Gemini key", "Delivery receipts and a full log", "Change sound mode, show a reminder or call a web address"],
       "publisher": {"@id": SITE + "/#org"}, "author": {"@id": SITE + "/about#lars"}}
VIDEO = {"@type": "VideoObject", "@id": SITE + "/#video", "name": "TextFox intro guide",
         "description": "The intro guide inside the TextFox Android app: your phone on autopilot, the four ways to fire a macro (schedule, one tap, incoming text, missed call), the guided permission setup, the optional Google Gemini key, and the ready screen.",
         "thumbnailUrl": SITE + "/media/textfox-intro-poster.jpg", "contentUrl": SITE + "/media/textfox-intro.mp4", "uploadDate": "2026-11-04T12:00:00+01:00", "duration": "PT37S",
         "transcript": "Your phone, on autopilot. TextFox sends text messages for you: on a schedule, as a reply or when you miss a call. Set it up once, and it just runs. Four ways to fire a macro: missed call, text people back when you can't pick up. And not just texts: a macro can also switch your sound mode or Do Not Disturb, show a reminder, or call a web address for your smart home. Hand it the keys: your phone blocks background apps until you say yes. Teach it to write: let Google Gemini write auto-replies and fresh variations of your scheduled texts with your own free API key. You're live: SMS access granted, background reliable, Gemini key saved, system armed."}


FILM_TRANSCRIPT = ("A 59-second motion film with music and robot sound effects, no voice-over. On-screen text: Meet TextFox. A tiny SMS robot that lives in your Android phone. "
    "Hi! I send your texts for you. A day on autopilot: 08:00 Good morning, Mom, Gemini rewrites it every morning. 10:41 Out of office, a text came in and TextFox answered it. "
    "11:27 Missed call text-back, the caller got a text seconds later. 17:40 Running late, one tap on the widget. Set it up once: on a schedule, with one tap, as a reply, after a missed call. "
    "AI writes it. You approve it. Google Gemini rewrites your scheduled text every time, so it never reads the same twice. Ask me first, or send automatically. "
    "Fires on the minute. Delivered. Runs on your phone, no account, no ads. Your phone texts for you. All day. Free on Google Play. textfox.app")
FILM_TRANSCRIPT_DA = ("En film på 59 sekunder med musik og robotlyde, uden speak. Tekst på skærmen: Mød TextFox. En lille sms-robot, der bor i din Android-telefon. "
    "Hej! Jeg sender dine sms'er for dig. En dag på autopilot: 08:00 Godmorgen, mor, Gemini omskriver den hver morgen. 10:41 Ikke på kontoret, der kom en sms, og TextFox svarede. "
    "11:27 Sms ved ubesvaret opkald, den, der ringede, fik en sms sekunder efter. 17:40 Forsinket, ét tryk på widgetten. Sæt det op én gang: på et fast tidspunkt, med ét tryk, som svar, efter et ubesvaret opkald. "
    "AI skriver den. Du godkender den. Google Gemini omskriver din planlagte sms hver gang, så den aldrig lyder ens. Spørg mig først, eller send automatisk. "
    "Sender på minuttet. Leveret. Kører på din telefon, ingen konto, ingen reklamer. Din telefon sms'er for dig. Hele dagen. Gratis på Google Play. textfox.app")


def filmfile(lang, ext):
    return f"/media/textfox-film{'-da' if lang == 'da' else ''}{ext}"


FILM = {"en": ("TextFox: your phone texts for you, all day", "A 59-second film about TextFox, the Android app that sends text messages for you: scheduled texts, auto-replies, missed-call text-back and AI-written variations with Google Gemini."),
        "da": ("TextFox: din telefon sender sms'er for dig, hele dagen", "En film på 59 sekunder om TextFox, Android-appen der sender sms'er for dig: planlagte sms'er, autosvar, sms ved ubesvaret opkald og AI-varianter med Google Gemini.")}


KEYFILM = {"en": ("Get your free Gemini API key for TextFox", "A 49-second guide: open Google AI Studio, create a free Gemini API key, copy it and paste it in TextFox under Settings → AI · Gemini, then test and save."),
           "da": ("Hent din gratis Gemini API-nøgle til TextFox", "En guide på 49 sekunder: åbn Google AI Studio, opret en gratis Gemini API-nøgle, kopiér den, og indsæt den i TextFox under Settings → AI · Gemini. Test og gem.")}
KEYFILM_TRANSCRIPT = ("Get your free Gemini API key. It takes about a minute. 01 Open Google AI Studio: go to aistudio.google.com and sign in with your Google account. "
    "02 Create an API key: on the API keys page, click Create API key; Google makes a new project for it. 03 Copy the key: it starts with AIza; keep it private, like a password. "
    "04 Paste it in TextFox: Settings, AI · Gemini. Paste the key, tap Test key, then Save. You're set: free tier, the key stays on your phone, AI is optional.")


KEYFILM_TRANSCRIPT_DA = ("Hent din gratis Gemini API-nøgle. Det tager cirka et minut. 01 Åbn Google AI Studio: gå til aistudio.google.com, og log ind med din Google-konto. "
    "02 Opret en API-nøgle: klik på Create API key på siden API keys; Google opretter et nyt projekt til den. 03 Kopiér nøglen: den starter med AIza; hold den hemmelig, ligesom en adgangskode. "
    "04 Indsæt den i TextFox: Settings, AI · Gemini. Indsæt nøglen, tryk på Test key og derefter Save. Du er klar: gratis, nøglen bliver på din telefon, AI er valgfrit.")


def keyfile(lang, ext):
    return f"/media/textfox-gemini-key{'-da' if lang == 'da' else ''}{ext}"


def keyfilm_schema(lang, url):
    name, desc = KEYFILM[lang]
    return {"@type": "VideoObject", "@id": url + "#gemini-key-guide", "name": name, "description": desc,
            "thumbnailUrl": [SITE + keyfile(lang, "-poster.jpg")], "uploadDate": "2026-11-05T15:00:00+01:00" if lang == "en" else "2026-11-06T12:00:00+01:00", "duration": "PT49S", "inLanguage": lang,
            "contentUrl": SITE + keyfile(lang, ".mp4"), "encodingFormat": "video/mp4", "width": 900, "height": 506, "isFamilyFriendly": True,
            "transcript": KEYFILM_TRANSCRIPT_DA if lang == "da" else KEYFILM_TRANSCRIPT, "about": {"@id": SITE + "/#app"}, "publisher": {"@id": SITE + "/#org"}, "creator": {"@id": SITE + "/about#lars"}, "isPartOf": {"@id": url}}


def film_schema(lang, url):
    name, desc = FILM[lang]
    return {"@type": "VideoObject", "@id": url + "#film", "name": name, "description": desc,
            "thumbnailUrl": [SITE + filmfile(lang, "-poster.jpg"), SITE + "/og/textfox-og.png"],
            "uploadDate": "2026-11-05T12:00:00+01:00" if lang == "en" else "2026-11-06T18:00:00+01:00", "duration": "PT59S", "inLanguage": lang,
            "contentUrl": SITE + filmfile(lang, ".mp4"), "encodingFormat": "video/mp4", "width": 900, "height": 506,
            "isFamilyFriendly": True, "transcript": FILM_TRANSCRIPT_DA if lang == "da" else FILM_TRANSCRIPT, "about": {"@id": SITE + "/#app"},
            "publisher": {"@id": SITE + "/#org"}, "creator": {"@id": SITE + "/about#lars"}, "isPartOf": {"@id": url}}


def head(meta, url):
    title, desc = meta["title"], meta["description"]
    lang = meta.get("lang", "en"); L = T[lang]
    og = SITE + "/og/textfox-og.png"
    graph = [ORG, PERSON, {"@type": "WebSite", "@id": SITE + "/#site", "url": SITE + "/", "name": "TextFox", "publisher": {"@id": SITE + "/#org"}, "inLanguage": "en"}]
    s = meta.get("schema", [])
    if "app" in s: graph.append(APP)
    if "faq" in s: graph.append(faq_schema("home", url, lang))
    if "video" in s: graph.append(VIDEO)
    if "film" in s: graph.append(film_schema(lang, url))
    if "keyfilm" in s: graph.append(keyfilm_schema(lang, url))
    if "page-faq" in s:
        graph.append(faq_schema(meta["faq"], url, lang))
        graph.append({"@type": "WebPage", "@id": url, "url": url, "name": title, "description": desc, "inLanguage": lang, "about": {"@id": SITE + "/#app"}, "isPartOf": {"@id": SITE + "/#site"}})
    if "person" in s:
        graph.append({"@type": "AboutPage", "@id": url, "url": url, "name": title, "description": desc, "inLanguage": lang, "mainEntity": {"@id": SITE + "/about#lars"}, "isPartOf": {"@id": SITE + "/#site"}})
    if meta.get("crumb"):
        graph.append({"@type": "BreadcrumbList", "itemListElement": [
            {"@type": "ListItem", "position": 1, "name": "TextFox", "item": abs_url(L["home"])},
            {"@type": "ListItem", "position": 2, "name": meta["crumb"], "item": url}]})
    ld = json.dumps({"@context": "https://schema.org", "@graph": graph}, ensure_ascii=False).replace("</", "<\\/")
    robots = "noindex, follow" if meta.get("noindex") else "index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1"
    e = html.escape
    alts = ""
    cp = counterpart(meta["path"])
    if cp:
        other_lang, other_path = cp
        en_path, da_path = (meta["path"], other_path) if lang == "en" else (other_path, meta["path"])
        alts = (f'\n<meta property="og:locale:alternate" content="{T[other_lang]["locale"]}">'
                f'\n<link rel="alternate" hreflang="en" href="{abs_url(en_path)}">\n<link rel="alternate" hreflang="da" href="{abs_url(da_path)}">'
                f'\n<link rel="alternate" hreflang="x-default" href="{abs_url(en_path)}">')
    preload = "".join(f'\n<link rel="modulepreload" href="{m}">' for m in (["/assets/js/three/three.module.min.js", "/assets/js/three/SVGLoader.min.js", "/assets/js/three/RoomEnvironment.min.js"] if meta.get("modules") else []))
    preload += "".join(f'\n<link rel="modulepreload" href="{m}?v={ver(m)}">' for m in meta.get("modules", []))
    noscript = '\n<noscript><style>.fox-stage .fox-wrap,.fox-stage .rings{opacity:1!important}</style></noscript>' if meta.get("modules") else ""
    preload += noscript
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>{e(title)}</title>
<meta name="description" content="{e(desc)}">
<meta name="robots" content="{robots}">
<link rel="canonical" href="{url}">
<meta name="theme-color" content="#090B0A">
<meta name="color-scheme" content="dark">
<link rel="icon" href="/favicon.svg" type="image/svg+xml">
<link rel="icon" href="/favicon-48.png" sizes="48x48" type="image/png">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<link rel="manifest" href="/site.webmanifest">
<link rel="preload" href="/assets/fonts/DMSans.woff2" as="font" type="font/woff2" crossorigin>
<link rel="preload" href="/assets/fonts/JetBrainsMono.woff2" as="font" type="font/woff2" crossorigin>
<link rel="stylesheet" href="/assets/site.css?v={ver('/assets/site.css')}">{preload}
<meta property="og:type" content="website">
<meta property="og:site_name" content="TextFox">
<meta property="og:locale" content="{L['locale']}">{alts}
<meta property="og:title" content="{e(title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{og}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="{e(L['og_alt'])}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{e(title)}">
<meta name="twitter:description" content="{e(desc)}">
<meta name="twitter:image" content="{og}">
<script type="application/ld+json">{ld}</script>
</head>"""


def nav(path, lang):
    L = T[lang]
    p = "" if path == L["home"] else L["home"].rstrip("/") if lang == "da" else "/"
    links = "".join(f'<a href="{p}#{a}">{n}</a>' for a, n in zip(["day", "triggers", "ai", "privacy", "faq"], L["nav"]))
    cp = counterpart(path)
    code, label, other = L["switch"]
    target = cp[1] if cp else T[other]["home"]
    return f"""<a class="skip" href="#main">{L["skip"]}</a>
<div class="grain" aria-hidden="true"></div>
<header class="nav"><div class="wrap">
  <a class="brand" href="{L["home"]}" aria-label="{L["home_aria"]}"><svg aria-hidden="true"><use href="#fox"/></svg>textfox</a>
  <nav class="nav-links" aria-label="Main">{links}</nav>
  <div class="nav-end"><a class="lang" href="{target}" hreflang="{other}" lang="{other}" title="{label}" aria-label="{label}">{code}</a><a class="btn btn-g btn-sm" href="{PLAY_URL}" rel="noopener">{L["get_app"]}</a></div>
</div></header>"""


def footer(lang):
    L = T[lang]; P = L["pages"]
    feats = "".join(f'<li><a href="{P[k][0]}">{P[k][1]}</a></li>' for k in ["sched", "auto", "missed", "tap", "ai"])
    return f"""<footer><div class="wrap">
  <div class="foot">
    <div><a class="brand" href="{L["home"]}" aria-label="{L["home_aria"]}"><svg aria-hidden="true"><use href="#fox"/></svg>textfox</a><p>{L["f_tag"]}</p></div>
    <div><h2>{L["f_features"]}</h2><ul>{feats}</ul></div>
    <div><h2>TextFox</h2><ul><li><a href="{PLAY_URL}" rel="noopener">Google Play</a></li><li><a href="{P["faq"][0]}">FAQ</a></li><li><a href="{P["about"][0]}">{L["about"]}</a></li><li><a href="{P["creators"][0]}">{P["creators"][1]}</a></li><li><a href="{L["privacy_href"]}">{L["privacy"]}</a></li></ul></div>
    <div><h2>{L["f_contact"]}</h2><ul><li><a href="mailto:{CONTACT}">{CONTACT}</a></li><li>{L["made_by"]} <a href="{P["about"][0]}">Lars Sohl</a></li><li><a href="https://www.kodekid.app" rel="noopener">{PUBLISHER}</a></li></ul></div>
  </div>
  <div class="legal"><span>© {datetime.date.today().year} TextFox · <a href="https://www.kodekid.app" rel="noopener">{PUBLISHER}</a></span><span><a href="{'/da/sitemap' if lang == 'da' else '/sitemap'}">Sitemap</a> · {L["no_track"]}</span></div>
</div></footer>"""


def related(path, lang="en"):
    L = T[lang]; P = L["pages"]
    links = "".join(f'<a href="{P[k][0]}"><span>{P[k][1]}</span><span class="g">→</span></a>' for k in ["sched", "auto", "missed", "tap", "ai"] if P[k][0] != path)
    return f'<section class="section tight"><div class="wrap"><h2 class="h3">{L["related_h"]}</h2><div class="related">{links}</div></div></section>'


SHARE_TEXT = "TextFox sends your texts for you: scheduled SMS, auto-replies and missed-call text-back for Android."
BRANDS = {
    "linkedin": '<path d="M4.98 3.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM3 9h4v12H3zM9 9h3.8v1.7h.05c.53-1 1.83-2.05 3.77-2.05C20.6 8.65 21 11.3 21 14.7V21h-4v-5.6c0-1.34-.02-3.06-1.86-3.06-1.87 0-2.15 1.46-2.15 2.96V21H9z"/>',
    "facebook": '<path d="M14 8.5V6.8c0-.8.5-1 .9-1H17V2.1L14.1 2C10.9 2 10.2 4.4 10.2 6v2.5H8V12h2.2v10H14V12h2.9l.4-3.5z"/>',
    "x": '<path d="M17.8 3H21l-7 8 8.2 10h-6.4l-5-6.2L5 21H1.8l7.5-8.6L1.5 3H8l4.5 5.7zm-1.1 16h1.8L7.3 4.9H5.4z"/>',
    "whatsapp": '<path d="M12 2a10 10 0 0 0-8.6 15.1L2 22l5-1.3A10 10 0 1 0 12 2zm5.2 13.9c-.2.6-1.3 1.2-1.8 1.2-.5.1-1 .2-3.3-.7-2.8-1.1-4.5-3.9-4.7-4.1-.1-.2-1.1-1.5-1.1-2.9s.7-2.1 1-2.4c.3-.3.6-.3.8-.3h.6c.2 0 .4 0 .6.5l.9 2.1c.1.2.1.4 0 .6l-.4.6-.4.4c-.1.2-.3.3-.1.6.2.3.8 1.3 1.7 2.1 1.2 1 2.1 1.3 2.4 1.5.3.1.5.1.6-.1l.9-1c.2-.3.4-.2.7-.1l2 1c.3.1.5.2.5.3.1.2.1.6-.1 1.2z"/>',
}


def share(url, lang="en"):
    L = T[lang]; SHARE_TEXT = L["share_text"]
    q = urllib.parse.quote
    links = [
        ("linkedin", "LinkedIn", "#0A66C2", f"https://www.linkedin.com/sharing/share-offsite/?url={q(url, safe='')}"),
        ("facebook", "Facebook", "#1877F2", f"https://www.facebook.com/sharer/sharer.php?u={q(url, safe='')}"),
        ("x", "X", "#E7ECE9", f"https://x.com/intent/post?text={q(SHARE_TEXT)}&url={q(url, safe='')}"),
        ("whatsapp", "WhatsApp", "#25D366", f"https://wa.me/?text={q(SHARE_TEXT + ' ' + url)}"),
    ]
    btns = "".join(f'<a class="sh" style="--b:{c}" href="{h}" target="_blank" rel="noopener"><svg viewBox="0 0 24 24" aria-hidden="true">{BRANDS[k]}</svg>{n}</a>' for k, n, c, h in links)
    mail = f'mailto:?subject={q(L["share_subject"])}&body={q(SHARE_TEXT + chr(10) + chr(10) + url)}'
    return f"""<section class="share" aria-labelledby="share-h"><div class="wrap"><div class="share-in">
  <div><p class="kicker">// share</p><h2 class="h3" id="share-h">{L["share_h"]}</h2><p class="body">{L["share_p"]}</p></div>
  <div class="share-btns">
    <button class="sh sh-native" type="button" data-share="{url}" data-text="{html.escape(SHARE_TEXT)}" hidden><svg viewBox="0 0 24 24" aria-hidden="true" class="st"><path d="M12 3v12M7.5 7.5 12 3l4.5 4.5M5 12v7.5h14V12"/></svg>{L["share"]}</button>
    {btns}
    <a class="sh" style="--b:#9AA79F" href="{mail}"><svg viewBox="0 0 24 24" aria-hidden="true" class="st"><rect x="3" y="5" width="18" height="14" rx="2.5"/><path d="m3.5 6.5 8.5 7 8.5-7"/></svg>{L["email"]}</a>
    <button class="sh" style="--b:#00E676" type="button" data-copy="{url}" data-label="{L["copy"]}"><svg viewBox="0 0 24 24" aria-hidden="true" class="st"><path d="M10 14a4.5 4.5 0 0 0 6.4 0l3-3a4.5 4.5 0 0 0-6.4-6.4l-1 1"/><path d="M14 10a4.5 4.5 0 0 0-6.4 0l-3 3a4.5 4.5 0 0 0 6.4 6.4l1-1"/></svg><span>{L["copy"]}</span></button>
  </div>
</div></div></section>"""


def final(lang="en"):
    L = T[lang]
    return f"""<section class="final"><div class="wrap"><div class="panel"><h2>{L["final_h"]}</h2>
<p>{L["final_p"]}</p><div class="cta-row">{play(lang)}</div><p class="cta-note">{L["cta_note"]}</p></div></div></section>"""


def render(meta, body):
    url = abs_url(meta["path"])
    lang = meta.get("lang", "en")
    body = (body.replace("{{PLAY}}", play(lang)).replace("{{CTA_NOTE}}", T[lang]["cta_note"]).replace("{{FAQ}}", faq_html(meta.get("faq", "home"), lang)).replace("{{CONTACT}}", CONTACT).replace("{{FILM_TRANSCRIPT}}", FILM_TRANSCRIPT_DA if lang == "da" else FILM_TRANSCRIPT)
            .replace("{{RELATED}}", related(meta["path"], lang)).replace("{{FINAL}}", final(lang)))
    scripts = "".join(f'<script src="{s}?v={ver(s)}" defer></script>' for s in ["/assets/bg.js", "/assets/site.js"] + meta.get("scripts", []))
    scripts += "".join(f'<script type="module" src="{s}?v={ver(s)}"></script>' for s in meta.get("modules", []))
    return f"""{head(meta, url)}
<body>
{ICONS}
{nav(meta["path"], lang)}
<main id="main">
{body}
{"" if meta.get("noindex") or meta["path"] == "/privacy" else share(url, lang)}
</main>
{footer(lang)}
{scripts}
</body>
</html>
"""


def privacy_page():
    md = (ROOT.parent / "docs" / "privacy.md").read_text()
    md = re.sub(r"^---.*?---\s*", "", md, flags=re.S)
    md = re.sub(r"^# .*\n", "", md, count=1, flags=re.M)
    body = markdown.markdown(md, extensions=["tables"])
    body = body.replace("<table>", '<div class="table-scroll"><table>').replace("</table>", "</table></div>")
    meta = {"out": "privacy.html", "path": "/privacy", "title": "Privacy Policy | TextFox",
            "description": "How TextFox handles your data: everything runs on your phone, with no account, no ads and no analytics. What leaves the phone, and why.", "crumb": "Privacy policy"}
    page = f"""<section class="page-hero"><div class="wrap">
<p class="crumbs"><a href="/">TextFox</a><span>/</span><span>Privacy policy</span></p>
<p class="kicker">// privacy</p><h1 class="h1 sub">Privacy policy</h1></div></section>
<section class="section tight"><div class="wrap"><div class="prose">{body}</div></div></section>"""
    return meta, page


SM = {"en": {"title": "Sitemap | TextFox", "desc": "Every page and video on textfox.app: features, guides, FAQ, privacy and the Danish version.",
              "h1": "Sitemap", "lede": "Every page and video on textfox.app.", "pages": "Pages", "videos": "Videos", "other": "Dansk version", "xml": "XML sitemap for search engines",
              "vids": [("/#film", "TextFox film, 59 seconds"), ("/#ai", "Get your free Gemini API key, 49-second guide"), ("/#setup", "The intro guide inside the app, 37 seconds")],
              "extra": [("/#faq", "Frequently asked questions"), ("/#privacy", "Your data, in short")]},
      "da": {"title": "Oversigt over siden | TextFox", "desc": "Alle sider og videoer på textfox.app: funktioner, guides, FAQ, privatliv og den engelske version.",
              "h1": "Oversigt", "lede": "Alle sider og videoer på textfox.app.", "pages": "Sider", "videos": "Videoer", "other": "English version", "xml": "XML-sitemap til søgemaskiner",
              "vids": [("/da#film", "TextFox-filmen, 59 sekunder"), ("/da#ai", "Hent din gratis Gemini API-nøgle, guide på 49 sekunder"), ("/da#setup", "Introguiden i appen, 37 sekunder (på engelsk)")],
              "extra": [("/da#faq", "Spørgsmål og svar"), ("/da#privacy", "Dine data, kort fortalt")]}}


def sitemap_page(lang, pages):
    S = SM[lang]; path = "/da/sitemap" if lang == "da" else "/sitemap"
    mine = [m for m, _ in pages if m.get("lang", "en") == lang and not m.get("noindex") and m["path"] != path]
    if lang == "da": mine.append({"path": "/privacy", "title": "Privatlivspolitik (på engelsk) | TextFox"})
    mine.sort(key=lambda m: (m["path"] != T[lang]["home"], m["path"] == "/privacy", m["title"].lower()))
    li = lambda h, t: f'<li><a href="{h}"><span>{html.escape(t)}</span><span class="g">→</span></a></li>'
    pg = "".join(li(m["path"], m["title"].split(" | ")[0]) for m in mine) + "".join(li(h, t) for h, t in S["extra"])
    vd = "".join(li(h, t) for h, t in S["vids"])
    other = "/sitemap" if lang == "da" else "/da/sitemap"
    body = f"""<section class="page-hero"><div class="wrap">
<p class="crumbs"><a href="{T[lang]["home"]}">TextFox</a><span>/</span><span>{S["h1"]}</span></p>
<p class="kicker">// sitemap</p><h1 class="h1 sub">{S["h1"]}</h1><p class="lede">{S["lede"]}</p></div></section>
<section class="section tight"><div class="wrap smap">
<div><h2 class="h3">{S["pages"]}</h2><ul class="smap-list">{pg}</ul></div>
<div><h2 class="h3">{S["videos"]}</h2><ul class="smap-list">{vd}</ul>
<h2 class="h3" style="margin-top:40px">More</h2><ul class="smap-list">{li(other, S["other"])}{li("/sitemap.xml", S["xml"])}</ul></div>
</div></section>"""
    if lang == "da": body = body.replace('>More<', '>Mere<')
    meta = {"out": path.lstrip("/") + ".html", "path": path, "lang": lang, "title": S["title"], "description": S["desc"], "crumb": S["h1"]}
    return meta, body


def main():
    pages = []
    for f in sorted((ROOT / "src" / "pages").rglob("*.html")):
        raw = f.read_text()
        m = re.match(r"<!--(\{.*?\})-->\n", raw, re.S)
        meta = json.loads(m.group(1))
        pages.append((meta, raw[m.end():]))
    pages.append(privacy_page())
    pages += [sitemap_page(l, pages) for l in ("en", "da")]
    for meta, body in pages:
        (PUB / meta["out"]).parent.mkdir(parents=True, exist_ok=True)
        (PUB / meta["out"]).write_text(render(meta, body))
        print("built", meta["out"])

    def sm_entry(path):
        alt = ""
        cp = counterpart(path)
        if cp:
            pair = {meta_lang: p for meta_lang, p in [("en" if cp[0] == "da" else "da", path), cp]}
            alt = "".join(f'<xhtml:link rel="alternate" hreflang="{l}" href="{abs_url(p)}"/>' for l, p in sorted(pair.items()))
        vid = ""
        if path in ("/", "/da"):
            lang = "da" if path == "/da" else "en"; name, desc = FILM[lang]
            vid = (f"<video:video><video:thumbnail_loc>{SITE}{filmfile(lang, '-poster.jpg')}</video:thumbnail_loc><video:title>{html.escape(name)}</video:title>"
                   f"<video:description>{html.escape(desc)}</video:description><video:content_loc>{SITE}{filmfile(lang, '.mp4')}</video:content_loc>"
                   f"<video:duration>59</video:duration><video:publication_date>{'2026-11-06T18:00:00+01:00' if lang == 'da' else '2026-11-05T12:00:00+01:00'}</video:publication_date><video:family_friendly>yes</video:family_friendly></video:video>")
            kn, kd = KEYFILM[lang]
            vid += (f"<video:video><video:thumbnail_loc>{SITE}{keyfile(lang, '-poster.jpg')}</video:thumbnail_loc><video:title>{html.escape(kn)}</video:title>"
                    f"<video:description>{html.escape(kd)}</video:description><video:content_loc>{SITE}{keyfile(lang, '.mp4')}</video:content_loc>"
                    f"<video:duration>49</video:duration><video:publication_date>{'2026-11-06T12:00:00+01:00' if lang == 'da' else '2026-11-05T15:00:00+01:00'}</video:publication_date><video:family_friendly>yes</video:family_friendly></video:video>")
        if path in ("/", "/da"):
            vid += (f"<video:video><video:thumbnail_loc>{SITE}/media/textfox-intro-poster.jpg</video:thumbnail_loc><video:title>{html.escape(VIDEO['name'])}</video:title>"
                    f"<video:description>{html.escape(VIDEO['description'])}</video:description><video:content_loc>{SITE}/media/textfox-intro.mp4</video:content_loc>"
                    f"<video:duration>37</video:duration><video:publication_date>2026-11-04T12:00:00+01:00</video:publication_date><video:family_friendly>yes</video:family_friendly></video:video>")
        return f"<url><loc>{abs_url(path)}</loc><lastmod>{TODAY}</lastmod>{alt}{vid}</url>"
    sm = "".join(sm_entry(m["path"]) for m, _ in pages if not m.get("noindex"))
    (PUB / "sitemap.xml").write_text(f'<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:video="http://www.google.com/schemas/sitemap-video/1.1">{sm}</urlset>\n')
    (PUB / "robots.txt").write_text(f"User-agent: *\nAllow: /\n\nSitemap: {SITE}/sitemap.xml\n")
    (PUB / "site.webmanifest").write_text(json.dumps({"name": "TextFox", "short_name": "TextFox", "start_url": "/", "display": "standalone",
        "background_color": "#090B0A", "theme_color": "#090B0A", "icons": [{"src": "/icon-192.png", "sizes": "192x192", "type": "image/png"}, {"src": "/icon-512.png", "sizes": "512x512", "type": "image/png"}]}, indent=1))
    faq_txt = "\n".join(f"- {q} {a}" for q, a in FAQ["home"])
    (PUB / "llms.txt").write_text(f"""# TextFox

> TextFox is an Android app (Android 8.0+) that sends text messages (SMS) automatically: on a schedule, as a reply to incoming texts, after a missed or declined call, or with one tap on a home-screen widget. It runs on the phone, with no account, no ads and no analytics. Optional AI features use the user's own free Google Gemini API key.

Maker: Lars Sohl (Denmark), founder of kodekid.app, klokly.app and larssohl.dk · Publisher: {PUBLISHER} · Contact: {CONTACT} · Google Play: {PLAY_URL}

## Pages
- [Home]({SITE}/): overview, a day with TextFox, setup, AI, privacy and FAQ
- [Scheduled text messages]({SITE}/scheduled-text-messages): time and weekdays, every 1–4 weeks, one date, random days, multiple recipients, AI variations
- [Auto-reply to text messages]({SITE}/auto-reply-sms): sender and keyword filters, quiet hours, fixed or AI-written replies
- [Missed-call text-back]({SITE}/missed-call-text-back): texts callers after a missed or declined call, optional caller filter
- [One-tap text widget]({SITE}/one-tap-text-widget): a home-screen widget per macro, one tap sends a ready-made text to one or many people
- [For creators]({SITE}/creators): a free promo code that unlocks TextFox for good for people making videos, reviews or posts about it, plus a press kit with films, logos, screenshots and copy
- [About the maker]({SITE}/about): Lars Sohl, Danish graphic designer, photographer and creative coder; founder of kodekid.app, klokly.app and larssohl.dk
- [Privacy policy]({SITE}/privacy)

## Dansk
- [TextFox på dansk]({SITE}/da): forside på dansk
- [Planlagte sms'er]({SITE}/da/planlagte-sms) · [Autosvar]({SITE}/da/autosvar-sms) · [Sms ved ubesvaret opkald]({SITE}/da/sms-ved-ubesvaret-opkald) · [Sms med ét tryk]({SITE}/da/sms-med-et-tryk) · [Til influencere]({SITE}/da/influencere) · [Om skaberen]({SITE}/da/om)

## Key facts
- Triggers: schedule, one tap (widget), incoming SMS (sender/keyword), missed call
- Actions: send an SMS to one or many recipients or reply to the sender/caller, change sound mode or Do Not Disturb, show a reminder notification, call a web address
- Reliability: exact alarms plus a backup scheduler; re-armed after restarts and updates; delivery receipts and a full log
- Not supported: iPhone, WhatsApp and other chat apps (SMS only)
- Price: free for 30 days, then a one-time in-app purchase of 7 USD on Google Play; no ads, no subscription; carrier SMS rates apply

## FAQ
{faq_txt}
""")
    print("built sitemap.xml robots.txt site.webmanifest llms.txt")


if __name__ == "__main__":
    main()
