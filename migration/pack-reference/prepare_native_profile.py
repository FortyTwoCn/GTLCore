#!/usr/bin/env python3
"""Prepare genuine pack-owned registrations/assets for the native mod validation profile.

This does not migrate the modpack's server recipes, quests, scripts or optional mods.
Existing files may be updated only if they match the original archive or this native profile.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
from zipfile import ZipFile

REFERENCE = Path(__file__).resolve().parent / "gregtech-leisure-1.4.5.2"
ARCHIVE_SHA256 = "e147d72e8c18613139d46ba7647cf5ec2bd10dce67b5be4c7ab9e325b2943997"
ROOT = ".minecraft/"
TEXT_REWRITES = (
    ("avaritia:item/resource/neutron_ingot", "avaritia:item/resource/neutron/neutron_ingot"),
    ("avaritia:item/resource/neutron_nugget", "avaritia:item/resource/neutron/neutron_nugget"),
    ("avaritia:item/resource/infinity_ingot", "avaritia:item/resource/infinity/infinity_ingot"),
    ("ae2:item/quartz_glass", "ae2:block/glass/quartz_glass_item"),
    ("creative_item_cell", "creative_storage_cell"),
    ("expatternprovider:fishbig", "extendedae:fishbig"),
    ('"forge_data"', '"neoforge_data"'),
    ('"forge:obj"', '"neoforge:obj"'),
)


def digest(contents):
    return hashlib.sha256(contents).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", type=Path, required=True)
    parser.add_argument("--game-directory", type=Path, required=True)
    parser.add_argument("--seed-world", type=Path, help="A previously validated native 1.21.1 world, copied for client GUI validation")
    parser.add_argument("--include-configs", action="store_true", help="Also copy the reference gtceu.yaml and gtlcore.yaml into a fresh profile")
    args = parser.parse_args()
    with args.archive.open("rb") as source:
        actual = hashlib.file_digest(source, "sha256").hexdigest()
    if actual != ARCHIVE_SHA256:
        raise SystemExit("Pack archive checksum mismatch; profile left intact")
    root = args.game_directory.resolve()
    pending = []
    with ZipFile(args.archive) as archive:
        for name in archive.namelist():
            if name.endswith("/") or not name.startswith(ROOT + "kubejs/assets/"):
                continue
            relative = Path(name.removeprefix(ROOT))
            if relative.is_absolute() or ".." in relative.parts:
                raise SystemExit("Invalid archive path; profile left intact")
            original = archive.read(name)
            native = original
            if relative.suffix in (".json", ".md", ".mcmeta"):
                text = original.decode("utf-8")
                for before, after in TEXT_REWRITES:
                    text = text.replace(before, after)
                native = text.encode("utf-8")
            pending.append((relative, original, native))
        for name in ("item.js", "block.js"):
            relative = Path("kubejs/startup_scripts") / name
            original = archive.read(ROOT + relative.as_posix())
            expected = (REFERENCE / "original/startup_scripts" / name).read_bytes()
            if original != expected:
                raise SystemExit("Registration scripts differ from the recorded source; profile left intact")
            native = (REFERENCE / "native/startup_scripts" / name).read_bytes()
            pending.append((relative, original, native))
        if args.include_configs:
            for name in ("gtceu.yaml", "gtlcore.yaml"):
                relative = Path("config") / name
                original = archive.read(ROOT + relative.as_posix())
                pending.append((relative, original, original))
    # Validate every replacement before writing any files.
    for relative, original, native in pending:
        target = root / relative
        if target.exists() and (not target.is_file() or target.read_bytes() not in (original, native)):
            raise SystemExit(f"Existing file differs from both source and native profile: {target}; profile left intact")
    world_target = root / "saves/Native Production Validation"
    if args.seed_world:
        if not (args.seed_world / "level.dat").is_file():
            raise SystemExit("Native seed world has no level.dat; profile left intact")
        if world_target.exists():
            raise SystemExit("Client validation world already exists; profile left intact")
    manifest = {
        "source_url": "https://www.curseforge.com/minecraft/modpacks/gregtech-leisure/files/8903963",
        "archive_sha256": actual,
        "license": "CC-BY-NC-SA-4.0",
        "scope": "pack-owned registration scripts and assets; complete production mods; pack server recipes/scripts are not loaded",
        "text_rewrites": list(TEXT_REWRITES),
        "files": {},
    }
    for relative, original, native in pending:
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(native)
        manifest["files"][relative.as_posix()] = {"source_sha256": digest(original), "native_sha256": digest(native)}
    license_contents = (REFERENCE / "LICENSE").read_bytes()
    (root / "GREGTECH-LEISURE-LICENSE").write_bytes(license_contents)
    if args.seed_world:
        shutil.copytree(args.seed_world, world_target, ignore=shutil.ignore_patterns("session.lock"))
        manifest["seed_world"] = {"level_dat_sha256": digest((world_target / "level.dat").read_bytes())}
    (root / "native-profile-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    print(f"Prepared native registration/asset profile at {root}: {len(pending)} source-verified files")


if __name__ == "__main__":
    main()
