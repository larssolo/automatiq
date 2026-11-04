#!/usr/bin/env python3
"""Builds the static TextFox website into ./public.

    python3 website/build.py          (needs: pip install markdown)

Pages live in src/pages/*.html with a JSON header comment. Shared head, nav,
footer, FAQ data and schema.org markup are generated here, so every page
stays consistent. The output in public/ is committed and served as-is by Vercel.
"""
import datetime, hashlib, html, json, pathlib, re

import markdown

ROOT = pathlib.Path(__file__).resolve().parent
PUB = ROOT / "public"
SITE = "https://www.textfox.app"

# ---- one place to change launch details ------------------------------------
PLAY_URL = "https://play.google.com/apps/testing/app.kodekid.automatiq"  # swap for the production listing at launch
CTA_NOTE = "Early access on Google Play · Free · Android 8.0+"
CONTACT = "hello@larssohl.dk"
PUBLISHER = "kodekid.app"
TODAY = datetime.date.today().isoformat()

FAQ = {
    "home": [
        ("What is TextFox?", "TextFox is a free Android app that sends text messages (SMS) for you automatically. You create a macro, which is a trigger plus a message, and TextFox sends it on a schedule, as a reply to an incoming text, after a missed call, or when you tap a home-screen widget."),
        ("Is TextFox free?", "Yes. TextFox is free, with no ads and no subscription. The optional AI features use your own free Google Gemini API key. Texts are sent through your normal mobile plan, so your carrier's usual SMS rates apply."),
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
    "missed": [
        ("Does it work when I decline a call?", "Yes. Both calls that ring out and calls you decline trigger the text-back."),
        ("Can I limit it to certain callers?", "Yes. An optional caller filter limits the text-back to the numbers you choose."),
        ("Which permissions does it need?", "Phone state and call log access, to notice a missed call, and SMS, to send the text. Both are used only on your phone and are never uploaded."),
        ("How fast is the text sent?", "Usually a few seconds after the call ends. Every send is logged and marked Delivered when your carrier confirms it."),
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
<symbol id="i-check" viewBox="0 0 24 24"><path d="m5 12.5 4.5 4.5L19 7.5"/></symbol>
<symbol id="i-spark" viewBox="0 0 24 24"><path d="M12 3.5 13.8 9l5.7 1.8-5.7 1.9L12 18.5l-1.8-5.8-5.7-1.9L10.2 9z"/></symbol>
</svg>"""

PLAY_SVG = '<svg width="18" height="20" viewBox="0 0 16 18" aria-hidden="true"><path d="M1 1.2v15.6c0 .5.5.8.9.6L15 9.6a.7.7 0 0 0 0-1.2L1.9.6C1.5.4 1 .7 1 1.2Z" fill="currentColor"/></svg>'
PLAY = f'<a class="btn btn-g play-btn" href="{PLAY_URL}" rel="noopener">{PLAY_SVG}<span><small>Get it on</small>Google Play</span></a>'


def ver(rel):
    return hashlib.sha1((PUB / rel.lstrip("/")).read_bytes()).hexdigest()[:8]


def faq_html(key):
    items = "".join(f'<details><summary>{html.escape(q)}</summary><div><p>{html.escape(a)}</p></div></details>' for q, a in FAQ[key])
    return f'<div class="faq">{items}</div>'


def faq_schema(key, url):
    return {"@type": "FAQPage", "@id": url + "#faq", "mainEntity": [
        {"@type": "Question", "name": q, "acceptedAnswer": {"@type": "Answer", "text": a}} for q, a in FAQ[key]]}


ORG = {"@type": "Organization", "@id": SITE + "/#org", "name": "TextFox", "url": SITE + "/",
       "logo": SITE + "/icon-512.png", "email": CONTACT, "parentOrganization": {"@type": "Organization", "name": PUBLISHER, "url": "https://kodekid.app"}}
APP = {"@type": "MobileApplication", "@id": SITE + "/#app", "name": "TextFox", "url": SITE + "/",
       "description": "TextFox is a free Android app that sends text messages automatically: scheduled SMS, auto-replies to incoming texts, missed-call text-back and one-tap widget messages, with optional AI-written variations using Google Gemini. Runs on the phone, with no account and no ads.",
       "operatingSystem": "Android 8.0 or later", "applicationCategory": "UtilitiesApplication", "applicationSubCategory": "SMS automation",
       "installUrl": PLAY_URL, "downloadUrl": PLAY_URL, "image": SITE + "/icon-512.png",
       "screenshot": [SITE + f"/media/screen-{s}.webp" for s in ("autopilot", "macros", "ai-variation", "phone-setup", "log")],
       "offers": {"@type": "Offer", "price": "0", "priceCurrency": "USD"},
       "featureList": ["Scheduled text messages by time, weekday, every 1–4 weeks, one date or random days", "Auto-reply to incoming SMS by sender or keyword, with quiet hours",
                       "Missed-call and declined-call text-back", "One-tap home-screen widgets", "Multiple recipients with personalised names",
                       "Optional AI variations and AI replies with your own Google Gemini key", "Delivery receipts and a full log", "Change sound mode, show a reminder or call a web address"],
       "publisher": {"@id": SITE + "/#org"}}
VIDEO = {"@type": "VideoObject", "@id": SITE + "/#video", "name": "TextFox intro guide",
         "description": "The intro guide inside the TextFox Android app: your phone on autopilot, the four ways to fire a macro (schedule, one tap, incoming text, missed call), the guided permission setup, the optional Google Gemini key, and the ready screen.",
         "thumbnailUrl": SITE + "/media/textfox-intro-poster.jpg", "contentUrl": SITE + "/media/textfox-intro.mp4", "uploadDate": "2026-11-04", "duration": "PT37S",
         "transcript": "Your phone, on autopilot. TextFox sends text messages for you: on a schedule, as a reply or when you miss a call. Set it up once, and it just runs. Four ways to fire a macro: missed call, text people back when you can't pick up. And not just texts: a macro can also switch your sound mode or Do Not Disturb, show a reminder, or call a web address for your smart home. Hand it the keys: your phone blocks background apps until you say yes. Teach it to write: let Google Gemini write auto-replies and fresh variations of your scheduled texts with your own free API key. You're live: SMS access granted, background reliable, Gemini key saved, system armed."}


def head(meta, url):
    title, desc = meta["title"], meta["description"]
    og = SITE + "/og/textfox-og.png"
    graph = [ORG, {"@type": "WebSite", "@id": SITE + "/#site", "url": SITE + "/", "name": "TextFox", "publisher": {"@id": SITE + "/#org"}, "inLanguage": "en"}]
    s = meta.get("schema", [])
    if "app" in s: graph.append(APP)
    if "faq" in s: graph.append(faq_schema("home", url))
    if "video" in s: graph.append(VIDEO)
    if "page-faq" in s:
        graph.append(faq_schema(meta["faq"], url))
        graph.append({"@type": "WebPage", "@id": url, "url": url, "name": title, "description": desc, "about": {"@id": SITE + "/#app"}, "isPartOf": {"@id": SITE + "/#site"}})
    if meta.get("crumb"):
        graph.append({"@type": "BreadcrumbList", "itemListElement": [
            {"@type": "ListItem", "position": 1, "name": "TextFox", "item": SITE + "/"},
            {"@type": "ListItem", "position": 2, "name": meta["crumb"], "item": url}]})
    ld = json.dumps({"@context": "https://schema.org", "@graph": graph}, ensure_ascii=False).replace("</", "<\\/")
    robots = "noindex, follow" if meta.get("noindex") else "index, follow, max-image-preview:large, max-snippet:-1, max-video-preview:-1"
    e = html.escape
    return f"""<!doctype html>
<html lang="en">
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
<link rel="stylesheet" href="/assets/site.css?v={ver('/assets/site.css')}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="TextFox">
<meta property="og:locale" content="en_US">
<meta property="og:title" content="{e(title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:url" content="{url}">
<meta property="og:image" content="{og}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="TextFox: your phone texts for you. Scheduled texts, auto-replies and missed-call text-back for Android.">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="{e(title)}">
<meta name="twitter:description" content="{e(desc)}">
<meta name="twitter:image" content="{og}">
<script type="application/ld+json">{ld}</script>
</head>"""


def nav(home):
    p = "" if home else "/"
    return f"""<a class="skip" href="#main">Skip to content</a>
<div class="grain" aria-hidden="true"></div>
<header class="nav"><div class="wrap">
  <a class="brand" href="/" aria-label="TextFox home"><svg aria-hidden="true"><use href="#fox"/></svg>textfox</a>
  <nav class="nav-links" aria-label="Main"><a href="{p}#day">A day</a><a href="{p}#triggers">Features</a><a href="{p}#ai">AI</a><a href="{p}#privacy">Privacy</a><a href="{p}#faq">FAQ</a></nav>
  <a class="btn btn-g btn-sm" href="{PLAY_URL}" rel="noopener">Get the app</a>
</div></header>"""


FOOTER = f"""<footer><div class="wrap">
  <div class="foot">
    <div><a class="brand" href="/" aria-label="TextFox home"><svg aria-hidden="true"><use href="#fox"/></svg>textfox</a><p>Scheduled texts, auto-replies and missed-call text-back for Android. Set it up once. It just runs.</p></div>
    <div><h2>Features</h2><ul><li><a href="/scheduled-text-messages">Scheduled texts</a></li><li><a href="/auto-reply-sms">Auto-reply</a></li><li><a href="/missed-call-text-back">Missed-call text-back</a></li><li><a href="/#ai">AI variations</a></li></ul></div>
    <div><h2>TextFox</h2><ul><li><a href="{PLAY_URL}" rel="noopener">Google Play</a></li><li><a href="/#faq">FAQ</a></li><li><a href="/privacy">Privacy policy</a></li></ul></div>
    <div><h2>Contact</h2><ul><li><a href="mailto:{CONTACT}">{CONTACT}</a></li><li>Made by {PUBLISHER}</li></ul></div>
  </div>
  <div class="legal"><span>© {datetime.date.today().year} TextFox · {PUBLISHER}</span><span>No cookies. No tracking.</span></div>
</div></footer>"""

RELATED_PAGES = [("/scheduled-text-messages", "Scheduled texts"), ("/auto-reply-sms", "Auto-reply"), ("/missed-call-text-back", "Missed-call text-back"), ("/#ai", "AI variations")]


def related(path):
    links = "".join(f'<a href="{h}"><span>{t}</span><span class="g">→</span></a>' for h, t in RELATED_PAGES if h != path)
    return f'<section class="section tight"><div class="wrap"><h2 class="h3">More of what TextFox does</h2><div class="related">{links}</div></div></section>'


FINAL = f"""<section class="final"><div class="wrap"><div class="panel"><h2>Set it up once.<br>It just runs.</h2>
<p>Free for Android 8.0 and newer. Runs on your phone: no account, no ads, no tracking.</p><div class="cta-row">{PLAY}</div><p class="cta-note">{CTA_NOTE}</p></div></div></section>"""


def render(meta, body):
    url = SITE + ("/" if meta["path"] == "/" else meta["path"])
    home = meta["path"] == "/"
    body = (body.replace("{{PLAY}}", PLAY).replace("{{CTA_NOTE}}", CTA_NOTE).replace("{{FAQ}}", faq_html(meta.get("faq", "home")))
            .replace("{{RELATED}}", related(meta["path"])).replace("{{FINAL}}", FINAL))
    scripts = "".join(f'<script src="{s}?v={ver(s)}" defer></script>' for s in ["/assets/site.js"] + meta.get("scripts", []))
    return f"""{head(meta, url)}
<body>
{ICONS}
{nav(home)}
<main id="main">
{body}
</main>
{FOOTER}
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


def main():
    pages = []
    for f in sorted((ROOT / "src" / "pages").glob("*.html")):
        raw = f.read_text()
        m = re.match(r"<!--(\{.*?\})-->\n", raw, re.S)
        meta = json.loads(m.group(1))
        pages.append((meta, raw[m.end():]))
    pages.append(privacy_page())
    for meta, body in pages:
        (PUB / meta["out"]).write_text(render(meta, body))
        print("built", meta["out"])

    urls = [(SITE + ("/" if m["path"] == "/" else m["path"])) for m, _ in pages if not m.get("noindex")]
    sm = "".join(f"<url><loc>{u}</loc><lastmod>{TODAY}</lastmod></url>" for u in urls)
    (PUB / "sitemap.xml").write_text(f'<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">{sm}</urlset>\n')
    (PUB / "robots.txt").write_text(f"User-agent: *\nAllow: /\n\nSitemap: {SITE}/sitemap.xml\n")
    (PUB / "site.webmanifest").write_text(json.dumps({"name": "TextFox", "short_name": "TextFox", "start_url": "/", "display": "standalone",
        "background_color": "#090B0A", "theme_color": "#090B0A", "icons": [{"src": "/icon-192.png", "sizes": "192x192", "type": "image/png"}, {"src": "/icon-512.png", "sizes": "512x512", "type": "image/png"}]}, indent=1))
    faq_txt = "\n".join(f"- {q} {a}" for q, a in FAQ["home"])
    (PUB / "llms.txt").write_text(f"""# TextFox

> TextFox is a free Android app (Android 8.0+) that sends text messages (SMS) automatically: on a schedule, as a reply to incoming texts, after a missed or declined call, or with one tap on a home-screen widget. It runs on the phone, with no account, no ads and no analytics. Optional AI features use the user's own free Google Gemini API key.

Publisher: {PUBLISHER} · Contact: {CONTACT} · Google Play: {PLAY_URL}

## Pages
- [Home]({SITE}/): overview, a day with TextFox, setup, AI, privacy and FAQ
- [Scheduled text messages]({SITE}/scheduled-text-messages): time and weekdays, every 1–4 weeks, one date, random days, multiple recipients, AI variations
- [Auto-reply to text messages]({SITE}/auto-reply-sms): sender and keyword filters, quiet hours, fixed or AI-written replies
- [Missed-call text-back]({SITE}/missed-call-text-back): texts callers after a missed or declined call, optional caller filter
- [Privacy policy]({SITE}/privacy)

## Key facts
- Triggers: schedule, one tap (widget), incoming SMS (sender/keyword), missed call
- Actions: send an SMS to one or many recipients or reply to the sender/caller, change sound mode or Do Not Disturb, show a reminder notification, call a web address
- Reliability: exact alarms plus a backup scheduler; re-armed after restarts and updates; delivery receipts and a full log
- Not supported: iPhone, WhatsApp and other chat apps (SMS only)
- Price: free, no ads, no subscription; carrier SMS rates apply

## FAQ
{faq_txt}
""")
    print("built sitemap.xml robots.txt site.webmanifest llms.txt")


if __name__ == "__main__":
    main()
