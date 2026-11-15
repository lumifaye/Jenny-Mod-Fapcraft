#!/usr/bin/env python3
"""Converts the 1.12.2 .lang files into 1.20.1 lang JSON for the Fabric build.

Run from fabric-1.20.1/:  python3 tools/convert_lang.py

Reads   ../src/main/resources/assets/sexmod/lang/*.lang
Writes  src/main/resources/assets/sexmod/lang/*.json

Keys whose format changed between the versions are renamed (RENAMES), keys
that only this port has get an English default (ADDED), and everything
belonging to content that is deliberately not ported is dropped (see
PORTING.md). All other keys are copied unchanged, so dialogue and menu
translations carry over as they are.
"""
import glob
import json
import os
import re

SOURCE = os.path.join("..", "src", "main", "resources", "assets", "sexmod", "lang")
TARGET = os.path.join("src", "main", "resources", "assets", "sexmod", "lang")

RENAMES = {
    "effect.horny_potion": "effect.sexmod.horny_potion",
    "potion.effect.horny_potion": "item.minecraft.potion.effect.horny_potion",
    "splash_potion.effect.horny_potion": "item.minecraft.splash_potion.effect.horny_potion",
    "lingering_potion.effect.horny_potion": "item.minecraft.lingering_potion.effect.horny_potion",
    "tipped_arrow.effect.horny_potion": "item.minecraft.tipped_arrow.effect.horny_potion",
}

ADDED = {
    "item.sexmod.jenny_spawn_egg": "Jenny Spawn Egg",
    "item.sexmod.ellie_spawn_egg": "Ellie Spawn Egg",
    "window.pornwarning.quit": "The game will close now.",
    "sexmod.menu.cost": "Costs %s",
    "sexmod.menu.cannot_afford": "You cannot afford this",
    "sexmod.hud.faster": "Hold [%s] to go faster",
    "sexmod.hud.finish": "Press [%s] to finish",
}

DROPPED = re.compile(r"mang", re.IGNORECASE)

# Two 1.12.2 files are named after codes Minecraft does not have, so the game
# never offered them; they are written under the codes of the languages they
# actually contain.
FILE_RENAMES = {
    "en_id": "id_id",  # Indonesian
    "ne_be": "nl_nl",  # Dutch
}


def convert_key(key):
    if key in RENAMES:
        return RENAMES[key]
    match = re.fullmatch(r"entity\.([a-z0-9_]+)\.name", key)
    if match:
        return "entity.sexmod." + match.group(1)
    match = re.fullmatch(r"item\.([a-z0-9_]+)\.name", key)
    if match:
        return "item.sexmod." + match.group(1)
    match = re.fullmatch(r"tile\.([a-z0-9_]+)\.name", key)
    if match:
        return "block.sexmod." + match.group(1)
    return key


def convert(path):
    entries = {}
    with open(path, encoding="utf-8-sig") as handle:
        for line in handle:
            line = line.rstrip("\r\n")
            if not line.strip() or line.lstrip().startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            if DROPPED.search(key):
                continue
            # 1.12.2 .lang files mark line breaks as a literal backslash-n.
            entries[convert_key(key)] = value.replace("\\n", "\n")
    return entries


def main():
    os.makedirs(TARGET, exist_ok=True)
    english = convert(os.path.join(SOURCE, "en_us.lang"))
    for path in sorted(glob.glob(os.path.join(SOURCE, "*.lang"))):
        entries = convert(path)
        for key, value in ADDED.items():
            entries.setdefault(key, value)
        code = os.path.splitext(os.path.basename(path))[0].lower()
        name = FILE_RENAMES.get(code, code) + ".json"
        with open(os.path.join(TARGET, name), "w", encoding="utf-8", newline="\n") as handle:
            json.dump(entries, handle, ensure_ascii=False, indent=2, sort_keys=True)
            handle.write("\n")
        missing = len(set(english) - set(entries))
        print("%-12s %4d keys, %3d not translated (fall back to English)" % (name, len(entries), missing))


if __name__ == "__main__":
    main()
