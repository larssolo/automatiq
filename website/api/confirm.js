// GET /api/confirm?e=&x=&s=&l=  -> marks the contact as subscribed and redirects to the thank-you page
const { verify, resend } = require('./_lib');

module.exports = async (req, res) => {
  const { e, x, s, l } = req.query || {};
  const da = l === 'da';
  const ok = da ? '/da/tilmeldt' : '/subscribed', bad = da ? '/da/tilmeldt?fejl=1' : '/subscribed?error=1';
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Referrer-Policy', 'no-referrer');
  const email = String(e || '').toLowerCase();
  if (!verify(email, x, s)) return res.redirect(302, bad);
  const r = await resend('PATCH', `/contacts/${encodeURIComponent(email)}`, { unsubscribed: false });
  return res.redirect(302, r.ok ? ok : bad);
};
