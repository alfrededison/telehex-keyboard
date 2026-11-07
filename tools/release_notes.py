#!/usr/bin/env python3
"""Prints the GitHub release notes for tag vX.Y.Z: its CHANGELOG.md section, then the commits since
the previous vX.Y.Z tag without the version bumps tools/release.sh makes ("Release X.Y.Z",
"Start X.Y.Z development").

    python3 tools/release_notes.py v0.3.1 [owner/repo]

The CHANGELOG is read from the tag, not the working tree. With owner/repo (GITHUB_REPOSITORY in
Actions) a compare link to the previous tag is added. Needs the full history and tags
(fetch-depth: 0).
"""
import re
import subprocess
import sys

BUMP = re.compile(r"^(Release \d+\.\d+\.\d+|Start \d+\.\d+\.\d+ development)$")
TAG = re.compile(r"^v(\d+\.\d+\.\d+)$")


def git(*args):
    return subprocess.run(["git", *args], text=True, capture_output=True, check=True).stdout


def changelog_section(tag, version):
    log = git("show", "%s:CHANGELOG.md" % tag)
    match = re.search(r"^## %s(?= |$).*$" % re.escape(version), log, re.M)
    if not match:
        sys.exit("CHANGELOG.md at %s has no '## %s' section" % (tag, version))
    body = log[match.end():].split("\n## ", 1)[0].strip()
    if not body:
        sys.exit("CHANGELOG.md at %s: '## %s' lists no changes" % (tag, version))
    return body


def previous_tag(tag):
    """The highest release tag reachable from the tag's parent; None for the first release."""
    parent = "%s^{commit}^" % tag
    if subprocess.run(["git", "rev-parse", "-q", "--verify", parent], capture_output=True).returncode:
        return None  # the tag is on the root commit
    # Only vX.Y.Z: "dev" is the moving prerelease tag.
    for name in git("tag", "--merged", parent, "--sort=-v:refname", "--list", "v*").split():
        if TAG.match(name):
            return name
    return None


def commits(prev, tag):
    span = "%s..%s" % (prev, tag) if prev else tag
    # showSignature would add lines that are not "sha\0subject".
    out = git("-c", "log.showSignature=false", "log", "--reverse", "--no-merges", "--format=%h%x00%s", span)
    lines = []
    for row in out.splitlines():
        sha, subject = row.split("\0", 1)
        if not BUMP.match(subject):
            lines.append("- %s (%s)" % (subject, sha))
    return lines


def main():
    if len(sys.argv) not in (2, 3):
        sys.exit("usage: tools/release_notes.py vX.Y.Z [owner/repo]")
    tag = sys.argv[1]
    repo = sys.argv[2] if len(sys.argv) == 3 else ""
    match = TAG.match(tag)
    if not match:
        sys.exit("not a release tag: %s" % tag)
    prev = previous_tag(tag)
    parts = ["## Thay đổi", changelog_section(tag, match.group(1))]
    listed = commits(prev, tag)
    if listed:
        parts += ["## Commits", "\n".join(listed)]
    if prev and repo:
        parts.append("**Full Changelog**: https://github.com/%s/compare/%s...%s" % (repo, prev, tag))
    print("\n\n".join(parts))


if __name__ == "__main__":
    main()
