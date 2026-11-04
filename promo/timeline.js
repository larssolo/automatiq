/*
 * Single source of truth for the promo's timing — shared by the frame renderer (browser) and the
 * soundtrack generator (Python reads the JSON export). Everything is expressed in BEATS on an
 * 80 BPM grid: 1 beat = 0.75 s, 1 bar = 4 beats = 3 s, 20 bars = 80 beats = 60 s.
 *
 * Because cuts, visual hits and sound effects all read the same beat numbers, a transition and
 * the whoosh that sells it can never drift apart.
 */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.TIMELINE = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  const BPM = 80;
  const BEAT = 60 / BPM;          // 0.75 s
  const TOTAL_BEATS = 80;          // 60.0 s
  const DURATION = TOTAL_BEATS * BEAT;

  // Shots: [id, inBeat, outBeat]. Adjacent shots butt-cut on the grid; transitions are drawn by
  // the shots themselves (each may render a little before `in` / after `out`, see scene/shots.js).
  const SHOTS = [
    ['hookSame', 0, 4],
    ['hookWhatIf', 4, 8],
    ['logo', 8, 12],
    ['appList', 12, 16],
    ['editor', 16, 24],
    ['trigIntro', 24, 26],
    ['trigScheduled', 26, 28],
    ['trigManual', 28, 30],
    ['trigReply', 30, 32],
    ['trigLocation', 32, 34],
    ['trigMissed', 34, 36],
    ['trigStates', 36, 38],
    ['trigOutro', 38, 40],
    ['aiReveal', 40, 42],
    ['aiReply', 42, 44],
    ['aiApprove', 44, 46],
    ['aiVary', 46, 48],
    ['featDelivered', 48, 50],
    ['featQuiet', 50, 52],
    ['featWidgets', 52, 54],
    ['featFolders', 54, 56],
    ['featHealth', 56, 58],
    ['featReboot', 58, 60],
    ['featVars', 60, 62],
    ['featBackup', 62, 64],
    ['trust', 64, 72],
    ['recap', 72, 76],
    ['endCard', 76, 80],
  ];

  // Music sections (beats). `full` sections run the whole groove; the others are arranged
  // separately in audio/soundtrack.py.
  const SECTIONS = [
    { id: 'intro', from: 0, to: 8 },       // bars 1-2  · filtered intro, build in bar 2
    { id: 'dropA', from: 8, to: 40 },      // bars 3-10 · groove; tape stop on the last beat
    { id: 'break', from: 40, to: 48 },     // bars 11-12· AI break, build in bar 12
    { id: 'dropB', from: 48, to: 64 },     // bars 13-16· full groove + lead
    { id: 'trust', from: 64, to: 72 },     // bars 17-18· low-passed groove, build in bar 18
    { id: 'outro', from: 72, to: 76 },     // bar 19    · final groove
    { id: 'end', from: 76, to: 80 },       // bar 20    · final hit + tail
  ];

  // Drum grid in 16th steps (16 per bar). Two-bar kick phrase; snare on 2 and 4. Every bar has a
  // kick on 1 and on 3 (steps 0 and 8) because that is where the picture cuts.
  const GROOVE = {
    kick: [[0, 6, 8], [0, 3, 8, 14]],
    snare: [4, 12],
  };

  // Silent pre-drop gaps [fromBeat, toBeat): everything but the effects tail is cut.
  const GAPS = [[7.5, 8], [47.5, 48], [71.5, 72]];

  // Sound-effect cues. `b` = beat. Types are implemented in audio/soundtrack.py.
  // dir: -1 = right→left, +1 = left→right, 0 = centre. len is in beats.
  const SFX = [
    // ── Hook: identical messages pile up, accelerating with the grid
    { b: 0, t: 'pop', pitch: 0 }, { b: 1, t: 'pop', pitch: 1 }, { b: 2, t: 'pop', pitch: 2 },
    { b: 2.5, t: 'pop', pitch: 3 }, { b: 3, t: 'pop', pitch: 4 }, { b: 3.25, t: 'pop', pitch: 5 },
    { b: 3.5, t: 'pop', pitch: 6 }, { b: 3.75, t: 'pop', pitch: 7 },
    { b: 4, t: 'glitch', len: 0.5 },
    { b: 4, t: 'riser', len: 3.5 },
    { b: 6, t: 'suck', len: 1.5 },
    { b: 6.5, t: 'heartbeat' }, { b: 7, t: 'heartbeat' }, { b: 7.25, t: 'heartbeat' },
    { b: 6, t: 'revCymbal', len: 2 },
    // ── Drop A: logo
    { b: 8, t: 'impact', size: 1.0 },
    { b: 8.25, t: 'typeRun', n: 10, len: 0.75 },
    { b: 9.5, t: 'shimmer', len: 1.0 },
    { b: 11, t: 'zoomIn', len: 1 },
    // ── App list
    { b: 12, t: 'whoosh', dir: 0, len: 0.75, up: 1 },
    { b: 12.5, t: 'tick' }, { b: 13, t: 'tick' }, { b: 13.5, t: 'tick' }, { b: 14, t: 'tick' },
    { b: 14.5, t: 'switchOn' },
    { b: 15, t: 'tap' },
    { b: 15.5, t: 'blob', len: 0.5 },
    // ── Editor
    { b: 16.25, t: 'typeRun', n: 11, len: 1.0 },
    { b: 18, t: 'pop', pitch: 3 }, { b: 18.5, t: 'pop', pitch: 5 },
    { b: 20.25, t: 'typeRun', n: 14, len: 1.0 },
    { b: 21.75, t: 'decode', len: 0.5 },
    { b: 23, t: 'tap' },
    { b: 23, t: 'tickRun', n: 8, len: 0.5 },
    { b: 23.5, t: 'whoosh', dir: 0, len: 0.5, up: 1 },
    // ── Triggers
    { b: 24, t: 'hit' },
    { b: 25, t: 'ding', note: 0 },
    { b: 25.5, t: 'zoomIn', len: 0.5 },
    { b: 26, t: 'tickRun', n: 4, len: 0.5 },
    { b: 27, t: 'ding', note: 1 },
    { b: 27.75, t: 'whoosh', dir: -1, len: 0.5 },
    { b: 28.5, t: 'tap' }, { b: 28.6, t: 'send' }, { b: 29, t: 'success' },
    { b: 29.75, t: 'whoosh', dir: 0, len: 0.4, up: 1 },
    { b: 30, t: 'pop', pitch: 2 }, { b: 31, t: 'send' },
    { b: 31.75, t: 'zoomIn', len: 0.4 },
    { b: 32, t: 'ping' }, { b: 33, t: 'ping' }, { b: 33, t: 'blip' }, { b: 33.15, t: 'send' },
    { b: 34, t: 'buzz', len: 0.9 }, { b: 35, t: 'missed' }, { b: 35.5, t: 'send' },
    { b: 36, t: 'plug' }, { b: 36.5, t: 'btBlip' }, { b: 37, t: 'wifi' }, { b: 37.5, t: 'hit' },
    { b: 37.75, t: 'whoosh', dir: 0, len: 0.5 },
    { b: 39, t: 'ding', note: 2 },
    { b: 39.25, t: 'tapeStop', len: 0.75 },
    { b: 39.75, t: 'crt' },
    // ── AI break
    { b: 41, t: 'sparkle', len: 1.5 },
    { b: 42, t: 'pop', pitch: 1 }, { b: 42.5, t: 'shimmer', len: 0.5 },
    { b: 43, t: 'typeRun', n: 12, len: 0.75 }, { b: 43.75, t: 'send' },
    { b: 43.75, t: 'flip', len: 0.5 },
    { b: 44, t: 'notify' }, { b: 45, t: 'tap' }, { b: 45.25, t: 'success' },
    { b: 46, t: 'flick' }, { b: 46.5, t: 'flick' }, { b: 47, t: 'flick' },
    { b: 44, t: 'riser', len: 3.5 },
    { b: 46, t: 'revCymbal', len: 2 },
    // ── Drop B: features (each lands on its own transition sound)
    { b: 48, t: 'impact', size: 0.9 },
    { b: 48.5, t: 'check' }, { b: 49, t: 'check', pitch: 1 },
    { b: 49.75, t: 'whoosh', dir: -1, len: 0.5 },
    { b: 50.75, t: 'chime', len: 1 },
    { b: 51.75, t: 'slice', len: 0.5 },
    { b: 52.5, t: 'tap' }, { b: 52.75, t: 'success' },
    { b: 53.75, t: 'zoomIn', len: 0.4 },
    { b: 54.5, t: 'flick' }, { b: 54.75, t: 'flick' }, { b: 55, t: 'flick' }, { b: 55.25, t: 'switchOn' },
    { b: 55.75, t: 'glitch', len: 0.25 },
    { b: 56.25, t: 'check' }, { b: 56.5, t: 'check', pitch: 1 }, { b: 56.75, t: 'check', pitch: 2 }, { b: 57, t: 'check', pitch: 3 },
    { b: 57.75, t: 'whoosh', dir: 0, len: 0.4 },
    { b: 58, t: 'powerDown' }, { b: 58.75, t: 'powerUp' },
    { b: 59.75, t: 'whoosh', dir: 0, len: 0.4, up: 1 },
    { b: 60.5, t: 'decode', len: 0.5 },
    { b: 61.75, t: 'whoosh', dir: 1, len: 0.5 },
    { b: 62.5, t: 'send' }, { b: 63, t: 'success' },
    // ── Trust
    { b: 63.75, t: 'downlifter', len: 1.5 },
    { b: 66, t: 'thud' }, { b: 67, t: 'thud' },
    { b: 68.5, t: 'switchOn' },
    { b: 70, t: 'riser', len: 1.5 },
    { b: 70, t: 'whoosh', dir: 1, len: 1.5 },
    { b: 70.5, t: 'revCymbal', len: 1.5 },
    // ── Recap + end card
    { b: 72, t: 'impact', size: 0.8 },
    { b: 73, t: 'hit' }, { b: 74, t: 'hit' }, { b: 75, t: 'hit' },
    { b: 75.5, t: 'revCymbal', len: 0.5 },
    { b: 76, t: 'impact', size: 1.1 },
    { b: 76.25, t: 'typeRun', n: 10, len: 0.75 },
    { b: 78, t: 'heartbeat' }, { b: 79, t: 'heartbeat' },
  ];

  return { BPM, BEAT, TOTAL_BEATS, DURATION, SHOTS, SECTIONS, GROOVE, GAPS, SFX };
});
