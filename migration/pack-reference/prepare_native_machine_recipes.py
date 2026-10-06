#!/usr/bin/env python3
"""Prepare the source-verified Space Elevator recipe slice, without importing other pack scripts."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

from prepare_native_profile import ARCHIVE_SHA256, ROOT, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--game-directory", required=True, type=Path)
    args = parser.parse_args()
    with args.archive.open("rb") as source:
        if hashlib.file_digest(source, "sha256").hexdigest() != ARCHIVE_SHA256:
            raise SystemExit("Reference pack archive checksum mismatch; profile left intact")
    with ZipFile(args.archive) as archive:
        original = archive.read(ROOT + "kubejs/server_scripts/misc.js")
    recipe = '''    gtr.space_elevator("1")
        .circuit(1)
        .duration(400)
        .CWUt(128)
        .EUt(GTValues.VA[GTValues.UV])'''
    if original.decode("utf-8").count(recipe) != 1:
        raise SystemExit("Original Space Elevator recipe differs; profile left intact")
    native = ("// GregTech Leisure 1.4.5.2 misc.js:75-79; CC-BY-NC-SA-4.0.\n"
              "// Only the original Space Elevator running recipe is included.\n"
              "ServerEvents.recipes((event) => {\n"
              "    const gtr = event.recipes.gtceu\n" + recipe + "\n})\n").encode("utf-8")
    root = args.game_directory.resolve()
    if not (root / "native-profile-manifest.json").is_file():
        raise SystemExit("Prepare the source-verified registration/asset profile first")
    relative = "kubejs/server_scripts/gtl_native_space_elevator.js"
    target = root / relative
    if target.exists() and (not target.is_file() or target.read_bytes() != native):
        raise SystemExit(f"Existing recipe differs; profile left intact: {target}")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(native)
    receipt = {
        "source_url": "https://www.curseforge.com/minecraft/modpacks/gregtech-leisure/files/8903963",
        "archive_sha256": ARCHIVE_SHA256,
        "license": "CC-BY-NC-SA-4.0",
        "scope": "original Space Elevator running recipe only; other pack recipes/scripts are not imported",
        "source_file": "kubejs/server_scripts/misc.js",
        "source_sha256": digest(original),
        "source_slice_sha256": digest(recipe.encode("utf-8")),
        "native_file": relative,
        "native_sha256": digest(native),
        "costs": {"circuit": 1, "duration": 400, "CWUt": 128, "EUt": "GTValues.VA[GTValues.UV]"},
    }
    (root / "native-machine-recipe-manifest.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(f"Prepared unchanged-cost native Space Elevator recipe: {target}")


if __name__ == "__main__":
    main()
