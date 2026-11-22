#!/usr/bin/env bash
# Renders vertical clips to out/<id>.mp4.   usage: marketing/shorts/make.sh [clipId ...]   (no ids = every clip in clips.json)
# Needs: node + playwright, ffmpeg, python3 with numpy. About 1.5 minutes per clip; JOBS=3 renders three at once.
set -euo pipefail
cd "$(dirname "$0")/../.."
export WORK=${WORK:-/tmp/shorts}; mkdir -p "$WORK" marketing/shorts/out
ids=("$@"); [ ${#ids[@]} -eq 0 ] && mapfile -t ids < <(python3 -c "import json;print('\n'.join(c['id'] for c in json.load(open('marketing/shorts/clips.json'))['clips']))")
(python3 -m http.server 8766 >/dev/null 2>&1 &) ; sleep 1; trap 'pkill -f "http.server 8766" || true' EXIT

one() {   # $1 = clip id, $2 = seed (position in clips.json, so a clip keeps its key when you re-render it)
  id=$1; d="$WORK/$id"; rm -rf "$d"; mkdir -p "$d"
  node marketing/shorts/render.js "$id" "$d" >"$d/render.log" 2>&1 || { cat "$d/render.log"; exit 1; }
  secs=$(python3 -c "import json;print(json.load(open('$d/meta.json'))['end'])")
  gf=$(python3 -c "import json;print(next((c.get('groove_from',0) for c in json.load(open('marketing/shorts/clips.json'))['clips'] if c['id']=='$id'),0))")
  python3 marketing/shorts/score.py "$secs" "$d/music.wav" "$2" "$gf" >/dev/null
  python3 marketing/film/sfx.py "$d/cues.json" "$d/music.wav" "$d/mix.wav" >/dev/null
  ffmpeg -v error -y -framerate 30 -i "$d/f%05d.jpg" -i "$d/mix.wav" -c:v libx264 -preset slow -crf 18 -pix_fmt yuv420p -movflags +faststart \
    -af "loudnorm=I=-14:TP=-1.5:LRA=11" -c:a aac -b:a 192k -shortest "marketing/shorts/out/$id.mp4"
  echo "→ marketing/shorts/out/$id.mp4 (${secs}s)"
}
export -f one
all=($(python3 -c "import json;print(' '.join(c['id'] for c in json.load(open('marketing/shorts/clips.json'))['clips']))"))
for id in "${ids[@]}"; do for i in "${!all[@]}"; do [ "${all[$i]}" = "$id" ] && echo "$id $((i+1))"; done; done | xargs -P "${JOBS:-3}" -L1 bash -c 'one $0 $1'
