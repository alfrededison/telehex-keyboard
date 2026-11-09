#!/usr/bin/env python3
"""Writes the Google Play "What's new" text for tag vX.Y.Z from its CHANGELOG.md section:

    python3 tools/play_whatsnew.py v0.7.2 out/whatsnew   ->  out/whatsnew/whatsnew-vi

Play takes plain text of at most 500 characters per language, so Markdown marks are dropped,
items become "• " lines, and items that do not fit are left out behind a pointer to the full
changelog. The CHANGELOG is read from the tag, as for the GitHub release notes.
"""
from pathlib import Path
import re
import sys

from release_notes import TAG, changelog_section

LIMIT = 500
MORE = "…và các thay đổi khác: xem CHANGELOG trên GitHub."


def plain(text):
    text = re.sub(r"\[([^\]]+)\]\([^)]*\)", r"\1", text)  # [label](url) -> label
    return re.sub(r"\*\*|__|`", "", text)


def items(body):
    """CHANGELOG items as single lines; a wrapped item continues on indented lines."""
    out = []
    for line in body.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if stripped.startswith(("- ", "* ")):
            out.append(stripped[2:].strip())
        elif out:
            out[-1] += " " + stripped
        else:
            out.append(stripped)
    return [plain(item) for item in out]


def shorten(text, limit):
    if len(text) <= limit:
        return text
    cut = text[:limit - 1].rsplit(" ", 1)[0].rstrip(" ,.;:")
    return cut + "…"


def whatsnew(body, limit=LIMIT):
    lines = ["• " + item for item in items(body)]
    text = "\n".join(lines)
    if len(text) <= limit:
        return text
    kept = []
    for line in lines:
        if len("\n".join(kept + [line, MORE])) > limit:
            break
        kept.append(line)
    if not kept:  # even the first item is too long: shorten it
        kept = [shorten(lines[0], limit - len(MORE) - 1)]
    return "\n".join(kept + [MORE])


def main():
    if len(sys.argv) != 3:
        sys.exit("usage: tools/play_whatsnew.py vX.Y.Z OUTPUT_DIR")
    tag, out = sys.argv[1], Path(sys.argv[2])
    match = TAG.match(tag)
    if not match:
        sys.exit("not a release tag: %s" % tag)
    out.mkdir(parents=True, exist_ok=True)
    text = whatsnew(changelog_section(tag, match.group(1)))
    # No trailing newline: the upload sends the file as is and Play counts it toward the 500.
    (out / "whatsnew-vi").write_text(text, encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
