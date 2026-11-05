#!/usr/bin/env bash
# Cuts release X.Y.Z (the current appVersion) and starts the next version in one go:
#
#   tools/release.sh [patch|minor|major]      (default: patch)
#
#   1. CHANGELOG.md "## X.Y.Z — unreleased" becomes "## X.Y.Z — YYYY-MM-DD"; commit "Release X.Y.Z".
#   2. Tag vX.Y.Z on that commit.
#   3. Bump appVersion to the next version, open "## <next> — unreleased" in CHANGELOG.md;
#      commit "Start <next> development".
#
# Nothing is pushed. Push both at once afterwards:
#
#   git push origin main vX.Y.Z
#
# The tag builds the signed release as a draft; main builds <next>-dev.1, whose versionCode is above
# the release, so dev builds keep installing over the release. Without step 3 every dev build would
# stay at X.Y.Z-dev.N, below X.Y.Z, and Android would refuse it as a downgrade.
set -euo pipefail
cd "$(dirname "$0")/.."

KIND="${1:-patch}"
case "$KIND" in patch|minor|major) ;; *) echo "usage: tools/release.sh [patch|minor|major]" >&2; exit 2 ;; esac

GRADLE=app/build.gradle.kts
CHANGELOG=CHANGELOG.md
BRANCH="${RELEASE_BRANCH:-main}"

[ "$(git rev-parse --abbrev-ref HEAD)" = "$BRANCH" ] || { echo "release from $BRANCH" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "working tree is not clean" >&2; exit 1; }

RELEASE=1 . tools/version.sh
CURRENT="$VERSION_NAME"
if git rev-parse -q --verify "refs/tags/v$CURRENT" >/dev/null; then
  echo "v$CURRENT already exists: bump appVersion first" >&2
  exit 1
fi
if ! grep -qx "## $CURRENT — unreleased" "$CHANGELOG"; then
  echo "$CHANGELOG needs a '## $CURRENT — unreleased' section" >&2
  exit 1
fi

IFS=. read -r MAJOR MINOR PATCH <<< "$CURRENT"
case "$KIND" in
  patch) NEXT="$MAJOR.$MINOR.$((PATCH + 1))" ;;
  minor) NEXT="$MAJOR.$((MINOR + 1)).0" ;;
  major) NEXT="$((MAJOR + 1)).0.0" ;;
esac
TODAY="${RELEASE_DATE:-$(date +%Y-%m-%d)}"

# 1. Release commit
python3 - "$CHANGELOG" "$CURRENT" "$TODAY" <<'PY'
import sys
path, version, today = sys.argv[1:]
text = open(path, encoding="utf-8").read()
old = "## %s — unreleased" % version
assert text.count(old) == 1, old
open(path, "w", encoding="utf-8").write(text.replace(old, "## %s — %s" % (version, today)))
PY
git add "$CHANGELOG"
git commit -q -m "Release $CURRENT"

# 2. Tag
git tag -a "v$CURRENT" -m "TeleHex $CURRENT"

# 3. Start the next version
python3 - "$GRADLE" "$CHANGELOG" "$CURRENT" "$NEXT" <<'PY'
import re, sys
gradle, changelog, current, nxt = sys.argv[1:]
text = open(gradle, encoding="utf-8").read()
line = 'val appVersion = "%s"' % current
assert re.search(r"^%s" % re.escape(line), text, re.M), line
open(gradle, "w", encoding="utf-8").write(re.sub(r"^%s" % re.escape(line), 'val appVersion = "%s"' % nxt, text, count=1, flags=re.M))
log = open(changelog, encoding="utf-8").read()
head = "# Changelog\n\n"
assert log.startswith(head), "CHANGELOG.md must start with '# Changelog'"
open(changelog, "w", encoding="utf-8").write(head + "## %s — unreleased\n\n" % nxt + log[len(head):])
PY
git add "$GRADLE" "$CHANGELOG"
git commit -q -m "Start $NEXT development"

RELEASE=0 . tools/version.sh
echo "Tagged v$CURRENT; main is now $VERSION_NAME (versionCode $VERSION_CODE)."
echo "Review, then push both:  git push origin $BRANCH v$CURRENT"
