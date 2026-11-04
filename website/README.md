# textfox.app

Static marketing site for TextFox, served by Vercel from `public/`.

- Edit pages in `src/pages/*.html`, shared head/nav/footer/FAQ/schema in `build.py`.
- Build: `pip install markdown && python3 build.py` (writes `public/*.html`, `sitemap.xml`, `robots.txt`, `llms.txt`).
- Icons and the Open Graph image: `node tools/render-assets.js` (needs Playwright).
- Launch: change `PLAY_URL` (and `CTA_NOTE`) at the top of `build.py`, rebuild, commit.
- The privacy page is generated from `../docs/privacy.md`.
- Vercel project settings: Root Directory `website`, Framework "Other", no build command (output is committed).

`concepts/` holds the four phase-1 design prototypes; it is not deployed.
