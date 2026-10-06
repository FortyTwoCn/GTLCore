#!/usr/bin/env python3
"""Prepare or verify the pinned source dependencies without resetting existing checkouts.

Run from a migrated GTLCore checkout. Paths come from its gradle.properties;
the ModDevGradle wrappers compile these sources and verify them again. The
optional ModularUI build uses the pinned GregTech repository's own script.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path
import subprocess
import sys


def run(*arguments, cwd=None, capture=False):
    result = subprocess.run([str(value) for value in arguments], cwd=cwd, check=True,
                            stdout=subprocess.PIPE if capture else None)
    return result.stdout if capture else None


def git(directory, *arguments):
    return run("git", "-C", directory, *arguments, capture=True)


def canonical_diff(value):
    # Git chooses object-id abbreviation lengths from repository object counts.
    # With the base commit pinned, identical complete diff bodies identify the same edits.
    return re.sub(rb"(?m)^index [0-9a-f]+\.\.[0-9a-f]+(?=\s|$)", b"index <objects>", value)


def properties(path):
    return dict(line.split("=", 1) for line in path.read_text().splitlines()
                if line and not line.startswith(("#", "!")) and "=" in line)


def checkout(path, url, revision, prepare):
    if not path.exists():
        if not prepare:
            raise ValueError(f"Missing pinned checkout: {path}")
        path.parent.mkdir(parents=True, exist_ok=True)
        run("git", "clone", "--no-checkout", "--filter=blob:none", url, path)
        git(path, "checkout", "--detach", revision)
    actual = git(path, "rev-parse", "HEAD").decode().strip()
    if actual != revision:
        raise ValueError(f"Existing {path} has revision {actual}, expected {revision}; left intact")


def clean_source(path):
    if git(path, "diff", "HEAD", "--binary") or git(path, "ls-files", "--others", "--exclude-standard").strip():
        raise ValueError(f"Existing native upstream source is modified: {path}; left intact")


def recorded_patch(path, manifest_path, patch_path, prepare):
    manifest = json.loads(manifest_path.read_text())
    patch = patch_path.read_bytes()
    if hashlib.sha256(patch).hexdigest() != manifest["patch_sha256"]:
        raise ValueError(f"Recorded patch hash differs: {patch_path}; source left intact")
    current = git(path, "diff", "HEAD", "--binary")
    if not current and prepare:
        git(path, "apply", "--check", patch_path)
        git(path, "apply", patch_path)
        current = git(path, "diff", "HEAD", "--binary")
    if canonical_diff(current) != canonical_diff(patch) or git(path, "ls-files", "--others", "--exclude-standard", "--", "src").strip():
        raise ValueError(f"Existing dependency differs from its recorded patch: {path}; left intact")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("prepare", "verify"))
    parser.add_argument("--core", type=Path, default=Path(__file__).resolve().parent.parent)
    parser.add_argument("--build-modularui", action="store_true",
                        help="Run the pinned GT scripts/build_modularui.py after source preparation (requires JDK 21)")
    args = parser.parse_args()
    if args.build_modularui and args.mode != "prepare":
        parser.error("--build-modularui is available only in prepare mode")
    core = args.core.resolve()
    values = properties(core / "gradle.properties")
    dependencies = core / "migration/dependencies"
    prepare = args.mode == "prepare"
    verified = {}

    def source(name):
        return (core / values[name + "_source"]).resolve()

    def pinned(name, url, revision):
        path = source(name)
        checkout(path, url, revision, prepare)
        verified[name] = {"directory": str(path), "revision": revision}
        return path

    gt_manifest = dependencies / "gtceu/patch-manifest.json"
    gt = json.loads(gt_manifest.read_text())
    if values["gtceu_revision"] != gt["revision"]:
        raise ValueError("GT property revision differs from the fixed API/patch manifest")
    gt_path = pinned("gtceu", gt["repository"], gt["revision"])
    recorded_patch(gt_path, gt_manifest, dependencies / "gtceu/ldlib-metadata.patch", prepare)

    raw_sources = {
        "gtmthings": "https://github.com/FortyTwoCn/GTMThings",
        "ftbultimine": "https://github.com/FTBTeam/FTB-Ultimine",
        "ftbranks": "https://github.com/FTBTeam/FTB-Ranks",
        "ae2jei": "https://github.com/Tamaized/AE2-JEI-Integration",
        "avaritia": "https://github.com/Nova-Committee/Re-Avaritia",
    }
    for name, url in raw_sources.items():
        path = pinned(name, url, values[name + "_revision"])
        clean_source(path)

    wt_manifest = dependencies / "ae2wtlib/patch-manifest.json"
    wt = json.loads(wt_manifest.read_text())
    wt_path = pinned("ae2wtlib", wt["source_url"], wt["revision"])
    recorded_patch(wt_path, wt_manifest, dependencies / "ae2wtlib/client-registration.patch", prepare)

    for name in ("mae2", "travelanchors", "adastrarocketed", "wildcardpattern"):
        manifest_path = dependencies / name / "patch-manifest.json"
        manifest = json.loads(manifest_path.read_text())
        key = manifest["source_property"].removesuffix("_source")
        if key + "_revision" in values and values[key + "_revision"] != manifest["revision"]:
            raise ValueError(f"{name} property revision differs from its source manifest")
        path = pinned(key, manifest["source_url"], manifest["revision"])
        run(sys.executable, dependencies / "native_source.py", args.mode,
            "--manifest", manifest_path, "--source", path)

    if args.build_modularui:
        # Tests and Maven-local publication use the exact user's GT API reference workflow.
        run(sys.executable, gt_path / "scripts/build_modularui.py", cwd=gt_path)

    print(json.dumps({"result": "PASS", "mode": args.mode, "pinned_sources": verified,
                      "modularui_built_this_run": args.build_modularui}, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from error
