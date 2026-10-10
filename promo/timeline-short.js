/*
 * The 15-second cut, derived from the full timeline: 20 beats on the same 80 BPM grid (1 beat = 0.75 s,
 * 5 bars). It shows the core function only: build one macro → it sends itself when something happens →
 * delivered.
 *
 *   beats  0-2   logo slam (the 4-beat logo shot at double speed)
 *          2-6   build a macro (the 8-beat editor at double speed) → "Den sender, når…"
 *          6-14  four triggers, 2 beats each: clock · widget tap · incoming SMS · leaving a place
 *         14-16  delivered ✓✓
 *         16-20  end card
 *
 * Sound effects are not re-authored: every cue of a reused shot is copied from the full timeline and
 * re-timed with the shot (and scaled with its speed), so a transition and the sound that sells it stay
 * together. Browser: loaded after timeline.js, replaces window.TIMELINE when the URL has ?cut=15.
 * Node: require('./timeline-short.js').
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory(require('./timeline.js'));
  else if (/[?&]cut=15/.test(root.location.search)) root.TIMELINE = factory(root.TIMELINE);
})(typeof self !== 'undefined' ? self : this, function (FULL) {
  const TOTAL_BEATS = 20;

  // id = shot built in scene/, src = the shot of the full film it is copied from, [a, b) = its place in
  // the cut, speed = source beats per cut beat, pre = how far before the source shot's start its
  // sound cues still belong to it (pre-roll such as a zoom whoosh or a reverse cymbal).
  const PLAN = [
    { id: 'logoShort', src: 'logo', a: 0, b: 2, speed: 2, pre: 0 },
    { id: 'editorShort', src: 'editor', a: 2, b: 6, speed: 2, pre: 0 },
    { id: 'trigScheduled', src: 'trigScheduled', a: 6, b: 8, speed: 1, pre: 0.5 },
    { id: 'trigManual', src: 'trigManual', a: 8, b: 10, speed: 1, pre: 0.5 },
    { id: 'trigReply', src: 'trigReply', a: 10, b: 12, speed: 1, pre: 0.5 },
    { id: 'trigLocation', src: 'trigLocation', a: 12, b: 14, speed: 1, pre: 0.5 },
    { id: 'featDelivered', src: 'featDelivered', a: 14, b: 16, speed: 1, pre: 0.5 },
    { id: 'endCard', src: 'endCard', a: 16, b: 20, speed: 1, pre: 0.5 },
  ];

  const srcShot = (id) => {
    const s = FULL.SHOTS.find((x) => x[0] === id);
    if (!s) throw new Error('timeline-short: no source shot ' + id);
    return s;
  };

  const SHOTS = PLAN.map((p) => [p.id, p.a, p.b]);

  const seen = new Set();
  const SFX = [];
  const add = (cue) => {
    if (cue.b < 0 || cue.b >= TOTAL_BEATS) return;
    const key = JSON.stringify(cue);
    if (seen.has(key)) return; // adjacent source windows overlap by their pre-roll; the copies coincide
    seen.add(key);
    SFX.push(cue);
  };
  PLAN.forEach((p) => {
    const [, sa, sb] = srcShot(p.src);
    FULL.SFX.forEach((c) => {
      if (c.b < sa - p.pre || c.b >= sb) return;
      const cue = Object.assign({}, c, { b: +(p.a + (c.b - sa) / p.speed).toFixed(4) });
      if (c.len != null) cue.len = +(c.len / p.speed).toFixed(4);
      add(cue);
    });
  });
  // transitions that exist only in this cut
  add({ b: 6, t: 'hit' }); // editor → first trigger lands on a hit
  add({ b: 13.25, t: 'revCymbal', len: 0.75 }); // leaving a place → the delivered impact
  SFX.sort((x, y) => x.b - y.b);

  return {
    BPM: FULL.BPM,
    BEAT: FULL.BEAT,
    TOTAL_BEATS,
    DURATION: TOTAL_BEATS * FULL.BEAT,
    SHOTS,
    // groove from beat 0 (no build-up: the first beat is the drop); bar 5 is the ring-out under the end card
    SECTIONS: [{ id: 'dropA', from: 0, to: 16 }, { id: 'end', from: 16, to: 20 }],
    GROOVE: FULL.GROOVE,
    GAPS: [],
    SFX,
    ARRANGEMENT: 'short',
  };
});
