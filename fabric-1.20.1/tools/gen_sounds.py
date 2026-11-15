#!/usr/bin/env python3
"""Writes the Fabric build's sounds.json: the subset of the 1.12.2 sounds.json
that this port actually registers.

Run from fabric-1.20.1/:  python3 tools/gen_sounds.py

The sound ids are derived from the SoundEvent[] arrays declared in
SoundHandler.java, using the same naming rule as SoundHandler.registerSounds():
GIRLS_JENNY_MOAN with 8 slots gives girls.jenny.moan.moan0 .. moan7. So
SoundHandler.java is the only place where sounds are listed; run this again
after changing it. The script stops with an error if an id is missing from the
1.12.2 sounds.json or its .ogg file does not exist, because either would make
the game log an error and play nothing.
"""
import json
import os
import re
import sys

HANDLER = os.path.join("src", "main", "java", "com", "trolmastercard", "sexmod", "util", "SoundHandler.java")
SHARED_ASSETS = os.path.join("..", "src", "main", "resources", "assets", "sexmod")
TARGET = os.path.join("src", "main", "resources", "assets", "sexmod", "sounds.json")


def registered_ids():
    with open(HANDLER, encoding="utf-8") as handle:
        source = handle.read()
    ids = []
    for name, size in re.findall(r"SoundEvent\[\]\s+(\w+)\s*=\s*new SoundEvent\[(\d+)\]", source):
        base = name.lower().replace("_", ".")
        parts = base.split(".")
        category = parts[2] if len(parts) > 2 else parts[1]
        ids.extend("%s.%s%d" % (base, category, index) for index in range(int(size)))
    return ids


def main():
    with open(os.path.join(SHARED_ASSETS, "sounds.json"), encoding="utf-8") as handle:
        all_sounds = json.load(handle)
    problems = []
    kept = {}
    for sound_id in registered_ids():
        entry = all_sounds.get(sound_id)
        if entry is None:
            problems.append("%s is not in the 1.12.2 sounds.json" % sound_id)
            continue
        for sound in entry["sounds"]:
            name = sound["name"] if isinstance(sound, dict) else sound
            path = os.path.join(SHARED_ASSETS, "sounds", *name.split(":", 1)[-1].split("/")) + ".ogg"
            if not os.path.isfile(path):
                problems.append("%s: missing file %s" % (sound_id, path))
        kept[sound_id] = entry
    if problems:
        sys.exit("sounds.json was not written:\n  " + "\n  ".join(problems))
    with open(TARGET, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(kept, handle, indent=2, sort_keys=True)
        handle.write("\n")
    print("wrote %s: %d of %d sounds" % (TARGET, len(kept), len(all_sounds)))


if __name__ == "__main__":
    main()
