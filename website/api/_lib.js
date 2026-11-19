// Shared helpers for the early-bird signup functions (not a route: files starting with "_" are ignored by Vercel).
const crypto = require('crypto');

const SITE = 'https://www.textfox.app';
const RESEND = 'https://api.resend.com';

function sign(email, exp) {
  return crypto.createHmac('sha256', process.env.TOKEN_SECRET).update(`${email}.${exp}`).digest('hex');
}

function verify(email, exp, sig) {
  if (!email || !sig || !(Number(exp) > Date.now())) return false;
  const a = Buffer.from(sign(email, exp)), b = Buffer.from(String(sig));
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

async function resend(method, path, body) {
  const r = await fetch(RESEND + path, {
    method,
    headers: { Authorization: `Bearer ${process.env.RESEND_API_KEY}`, 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await r.json(); } catch {}
  return { ok: r.ok, status: r.status, data };
}

const esc = s => String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

function confirmMail(lang, name, link) {
  const da = lang === 'da';
  const hi = name ? esc(name) : (da ? 'der' : 'there');
  const t = da
    ? { subject: 'Bekræft din plads på TextFox early bird-listen', h: `Hej ${hi}`, p: 'Tak, fordi du vil have besked, når TextFox udkommer på Google Play. Bekræft din e-mail, så sender vi dig linket og din <strong>50 % early bird-rabat</strong>, så snart appen er ude.', b: 'Bekræft min e-mail', f: 'Hvis du ikke har tilmeldt dig, kan du bare ignorere denne mail, så sker der ingenting.' }
    : { subject: 'Confirm your spot on the TextFox early-bird list', h: `Hi ${hi}`, p: 'Thanks for wanting to know when TextFox launches on Google Play. Confirm your email and we\'ll send you the link and your <strong>50% early-bird discount</strong> as soon as the app is out.', b: 'Confirm my email', f: 'If you didn\'t sign up, just ignore this email and nothing will happen.' };
  const html = `<!doctype html><html lang="${lang}"><body style="margin:0;background:#090B0A;padding:32px 16px;font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#E8F3EC">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0"><tr><td align="center">
<table role="presentation" width="480" style="max-width:480px;background:#131916;border:1px solid #1d2a24;border-radius:20px"><tr><td style="padding:36px 32px">
<p style="margin:0 0 20px;font-size:20px;font-weight:700;color:#00E676">TextFox</p>
<h1 style="margin:0 0 14px;font-size:24px;line-height:1.2;color:#fff">${t.h},</h1>
<p style="margin:0 0 26px;font-size:16px;line-height:1.6;color:#B9C7BF">${t.p}</p>
<p style="margin:0 0 26px"><a href="${link}" style="display:inline-block;background:#00E676;color:#06130C;text-decoration:none;font-weight:700;font-size:16px;padding:14px 26px;border-radius:999px">${t.b}</a></p>
<p style="margin:0;font-size:13px;line-height:1.5;color:#7F8F86">${t.f}</p>
</td></tr></table></td></tr></table></body></html>`;
  const text = `${t.h.replace(/<[^>]+>/g, '')},\n\n${t.p.replace(/<[^>]+>/g, '')}\n\n${link}\n\n${t.f}`;
  return { subject: t.subject, html, text };
}

module.exports = { SITE, sign, verify, resend, confirmMail };
