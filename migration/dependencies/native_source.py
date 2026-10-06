#!/usr/bin/env python3
"""Prepare or verify a complete, pinned native dependency without overwriting local edits."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path


def git(source, *args):
    return subprocess.check_output(["git", "-C", str(source), *args])


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_files(source):
    return {
        path.relative_to(source).as_posix(): sha256(path)
        for path in (source / "src").rglob("*")
        if path.is_file() and ".cache" not in path.relative_to(source).parts
    }


def verify(source, manifest, patch):
    revision = git(source, "rev-parse", "HEAD").decode().strip()
    if revision != manifest["revision"]:
        raise ValueError(f"Expected revision {manifest['revision']}, found {revision}")
    if sha256(patch) != manifest["patch_sha256"]:
        raise ValueError("Recorded native patch does not match its SHA-256")
    actual = source_files(source)
    expected = manifest["source_files_sha256"]
    changes = sorted(set(actual) ^ set(expected))
    changes += sorted(path for path in set(actual) & set(expected) if actual[path] != expected[path])
    for path, digest in manifest["licenses_sha256"].items():
        if not (source / path).is_file() or sha256(source / path) != digest:
            changes.append(path)
    if changes:
        raise ValueError("Dependency differs from its recorded native source: " + ", ".join(changes[:12]))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=["prepare", "verify"])
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--reference", type=Path)
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    patch = args.manifest.resolve().parent / manifest["patch_file"]
    source = args.source.resolve()
    if sha256(patch) != manifest["patch_sha256"]:
        raise SystemExit("Native patch checksum mismatch; source left intact")
    if args.mode == "prepare":
        if not source.exists():
            if args.reference:
                git(args.reference, "worktree", "add", "--detach", str(source), manifest["revision"])
            else:
                subprocess.run(["git", "clone", "--no-checkout", manifest["source_url"], str(source)], check=True)
                git(source, "checkout", "--detach", manifest["revision"])
        try:
            verify(source, manifest, patch)
        except ValueError:
            revision = git(source, "rev-parse", "HEAD").decode().strip()
            edits = git(source, "diff", "HEAD", "--binary", "--", "src")
            untracked = git(source, "ls-files", "--others", "--exclude-standard", "--", "src").strip()
            if revision != manifest["revision"] or edits or untracked:
                raise SystemExit("Existing dependency does not match the pinned clean source or native port; local files left intact")
            git(source, "apply", "--check", str(patch))
            git(source, "apply", str(patch))
    try:
        verify(source, manifest, patch)
    except ValueError as exc:
        raise SystemExit(str(exc)) from exc
    print(f"Verified {manifest['name']} at {source}: {len(manifest['source_files_sha256'])} native source/resource files")


if __name__ == "__main__":
    main()
