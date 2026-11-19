// POST /api/subscribe  {name, email, lang, consent, t, website}
// Adds the person to the Resend "early bird" segment as unsubscribed (pending) and emails a signed confirmation link.
const { SITE, sign, resend, confirmMail } = require('./_lib');

const EMAIL = /^[^\s@<>"',;:]+@[^\s@<>"',;:]+\.[^\s@<>"',;:]{2,}$/;
const ORIGINS = new Set([SITE, 'https://textfox.app']);
const done = (res, code, body) => { res.setHeader('Cache-Control', 'no-store'); res.status(code).json(body); };

module.exports = async (req, res) => {
  if (req.method !== 'POST') { res.setHeader('Allow', 'POST'); return done(res, 405, { ok: false }); }
  const origin = req.headers.origin || '';
  if (origin && !ORIGINS.has(origin) && !/^https:\/\/textfox[a-z0-9-]*\.vercel\.app$/.test(origin)) return done(res, 403, { ok: false });

  let b = req.body;
  if (typeof b === 'string') { try { b = JSON.parse(b); } catch { b = {}; } }
  b = b || {};

  // bots: the hidden field must be empty and the form must have been open for a human-ish moment
  const age = Date.now() - Number(b.t);
  if (b.website || !(age > 1500 && age < 86400000)) return done(res, 200, { ok: true }); // pretend success, store nothing

  const email = String(b.email || '').trim().toLowerCase();
  const name = String(b.name || '').replace(/[\r\n<>]/g, ' ').trim().slice(0, 80);
  const lang = b.lang === 'da' ? 'da' : 'en';
  if (!name || !EMAIL.test(email) || email.length > 254) return done(res, 400, { ok: false, error: 'invalid' });
  if (b.consent !== true) return done(res, 400, { ok: false, error: 'consent' });

  const seg = process.env.RESEND_SEGMENT_ID;
  const first = name.split(/\s+/)[0], last = name.split(/\s+/).slice(1).join(' ');
  const created = await resend('POST', '/contacts', { email, first_name: first, last_name: last || undefined, unsubscribed: true, segments: [{ id: seg }] });
  if (!created.ok) {
    // already a contact: make sure they are in the segment and carry on (the confirmation mail is re-sent)
    const msg = JSON.stringify(created.data || '').toLowerCase();
    if (created.status === 409 || msg.includes('already exist')) {
      await resend('POST', `/contacts/${encodeURIComponent(email)}/segments/${seg}`);
    } else return done(res, 502, { ok: false, error: 'store' });
  }

  const exp = Date.now() + 14 * 86400000;
  const link = `${SITE}/api/confirm?e=${encodeURIComponent(email)}&x=${exp}&s=${sign(email, exp)}&l=${lang}`;
  const m = confirmMail(lang, name.split(/\s+/)[0], link);
  const sent = await resend('POST', '/emails', { from: process.env.MAIL_FROM, to: [email], subject: m.subject, html: m.html, text: m.text });
  if (!sent.ok) return done(res, 502, { ok: false, error: 'mail' });
  return done(res, 200, { ok: true });
};
