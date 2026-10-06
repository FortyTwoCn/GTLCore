#!/usr/bin/env python3
"""Prepare the pinned GT API source with the missing optional-mixin declaration repaired."""
import argparse
import re
import subprocess
from pathlib import Path

REVISION = "c72dc16b52795cbb0456b6e15e13cc4a85c0ed1b"
PATCH = Path(__file__).with_name("ldlib-metadata.patch")


def git(directory, *args):
    return subprocess.check_output(["git", "-C", str(directory), *args], text=True)


def canonical_diff(value):
    return re.sub(r"(?m)^index [0-9a-f]+\.\.[0-9a-f]+(?=\s|$)", "index <objects>", value)


def main():
    workspace = Path(__file__).resolve().parents[4]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", type=Path, default=workspace / "GregTech-1.21")
    parser.add_argument("--output", type=Path, default=workspace / "GregTech-GTL-1.21")
    args = parser.parse_args()
    if not args.output.exists():
        git(args.reference, "worktree", "add", "--detach", str(args.output), REVISION)
    actual = git(args.output, "rev-parse", "HEAD").strip()
    if actual != REVISION:
        raise SystemExit(f"Expected GT API source {REVISION}, got {actual}; existing checkout left intact")
    expected = PATCH.read_text()
    current = git(args.output, "diff", "HEAD", "--binary")
    if not current:
        git(args.output, "apply", "--check", str(PATCH))
        git(args.output, "apply", str(PATCH))
    elif canonical_diff(current) != canonical_diff(expected):
        raise SystemExit("Existing dependency edits differ from the recorded patch; checkout left intact")
    if canonical_diff(git(args.output, "diff", "HEAD", "--binary")) != canonical_diff(expected):
        raise SystemExit("Dependency patch verification failed")
    print(f"Prepared {args.output.resolve()}; API source remains pinned to {REVISION}")


if __name__ == "__main__":
    main()
