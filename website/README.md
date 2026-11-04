# textfox.app

Static marketing site for TextFox, served by Vercel from `public/`.

- Edit pages in `src/pages/*.html` (English, at the root) and `src/pages/da/*.html` (Danish, under `/da`). Shared head/nav/footer/FAQ/schema and all UI strings per language (`T`, `FAQ`, `FAQ_DA`) are in `build.py`.
- Translations are paired in `PAIRS` in `build.py`; that drives hreflang tags, the sitemap alternates and the EN/DA switch. A new page needs its pair added there and its route added to `vercel.json`.
- Texts set from JavaScript (the day scene, orbiting texts, AI demo) pick Danish when `<html lang="da">`.
- New page? Add its slug to the `rewrites` and `redirects` in `vercel.json` (clean URLs are explicit so the Google verification `.html` file is served as-is).
- Build: `pip install markdown && python3 build.py` (writes `public/*.html`, `sitemap.xml`, `robots.txt`, `llms.txt`).
- Icons and the Open Graph image: `node tools/render-assets.js` (needs Playwright).
- Launch: change `PLAY_URL` (and `CTA_NOTE`) at the top of `build.py`, rebuild, commit.
- The privacy page is generated from `../docs/privacy.md`.
- Vercel project settings: Root Directory `website`, Framework "Other", no build command (output is committed).

`concepts/` holds the four phase-1 design prototypes; it is not deployed.
