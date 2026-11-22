#!/usr/bin/env python3
"""Checks public/macros/*.json against the app's own import DTO (app/.../util/MacroJson.kt).

    python3 tools/check_templates.py

Every property of MacroDto without a default value must be present in each macro (kotlinx.serialization
treats a missing nullable-without-default as an error), and the enum values must exist in Macro.kt.
Run it after changing either the templates or the app's MacroDto.
"""
import json, pathlib, re, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
dto = (ROOT / "app/src/main/java/com/vibeactions/util/MacroJson.kt").read_text()
model = (ROOT / "app/src/main/java/com/vibeactions/domain/model/Macro.kt").read_text()
body = re.search(r"data class MacroDto\((.*?)\n\)", dto, re.S).group(1)
fields = {}
for part in re.split(r",\s*(?=val )", re.sub(r"\s+", " ", body)):
    m = re.match(r"\s*val (\w+):\s*([^=]+?)\s*(=.*)?$", part.strip().rstrip(","))
    if m: fields[m.group(1)] = bool(m.group(3))          # name -> has default
assert len(fields) > 30, f"DTO parse failed: {fields}"
required = {k for k, d in fields.items() if not d}
enum = lambda n: set(re.search(rf"enum class {n} \{{ ([^}}]*) \}}", model).group(1).replace(" ", "").split(","))
ENUMS = {"triggerType": enum("TriggerType"), "actionType": enum("ActionType"), "soundMode": enum("SoundMode"), "aiSendMode": enum("AiSendMode")}
bad = 0
files = sorted((ROOT / "website/public/macros").glob("*.json"))
for f in files:
    for i, m in enumerate(json.loads(f.read_text())):
        miss = required - m.keys(); extra = m.keys() - fields.keys()
        wrong = [f"{k}={m[k]!r}" for k, vals in ENUMS.items() if k in m and m[k] not in vals]
        if miss or extra or wrong:
            bad += 1; print(f"{f.name}[{i}] missing={sorted(miss)} unknown={sorted(extra)} bad-enum={wrong}")
print(f"{len(files)} files, {len(required)} required fields, {'OK' if not bad else str(bad) + ' problems'}")
sys.exit(1 if bad else 0)
