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
# Every check runs before the first write, and a failure after that (a commit hook, GPG) resets the
# branch to where it started and deletes the tag, so the script can simply be run again.
#
# Nothing is pushed. Push both refs atomically afterwards, so the tag never lands without main:
#
#   git push --atomic origin main vX.Y.Z
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
REMOTE="${RELEASE_REMOTE:-origin}"

fail() { echo "$*" >&2; exit 1; }

# ---- Checks (nothing is written before all of them pass) ----

[ "$(git rev-parse --abbrev-ref HEAD)" = "$BRANCH" ] || fail "release from $BRANCH"
[ -z "$(git status --porcelain)" ] || fail "working tree is not clean"

RELEASE=1 . tools/version.sh
CURRENT="$VERSION_NAME"
TAG="v$CURRENT"

git remote get-url "$REMOTE" >/dev/null 2>&1 || fail "no remote '$REMOTE' (set RELEASE_REMOTE)"
git fetch -q --tags "$REMOTE" || fail "cannot fetch $REMOTE"
if git rev-parse -q --verify "refs/remotes/$REMOTE/$BRANCH" >/dev/null; then
  git merge-base --is-ancestor "$REMOTE/$BRANCH" HEAD ||
    fail "$BRANCH is behind or has diverged from $REMOTE/$BRANCH: pull first"
fi
if git ls-remote --exit-code --tags "$REMOTE" "refs/tags/$TAG" >/dev/null 2>&1; then
  fail "$TAG already exists on $REMOTE: bump appVersion first"
fi
if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  fail "$TAG already exists locally: bump appVersion first"
fi

IFS=. read -r MAJOR MINOR PATCH <<< "$CURRENT"
case "$KIND" in
  patch) NEXT="$MAJOR.$MINOR.$((PATCH + 1))" ;;
  minor) NEXT="$MAJOR.$((MINOR + 1)).0" ;;
  major) NEXT="$((MAJOR + 1)).0.0" ;;
esac
TODAY="${RELEASE_DATE:-$(date +%Y-%m-%d)}"

# Both file edits, checked up front: exactly one unreleased section, the changelog header, the
# appVersion line. "check" validates only; "release" and "bump" write.
edit() {
  python3 - "$1" "$GRADLE" "$CHANGELOG" "$CURRENT" "$NEXT" "$TODAY" <<'PY'
import re, sys
mode, gradle, changelog, current, nxt, today = sys.argv[1:]
head = "# Changelog\n\n"
section = "## %s — unreleased" % current
line = re.compile(r'^val appVersion = "%s"' % re.escape(current), re.M)
log = open(changelog, encoding="utf-8").read()
build = open(gradle, encoding="utf-8").read()
if mode == "check":
    problems = []
    if not log.startswith(head):
        problems.append("%s must start with '# Changelog' and a blank line" % changelog)
    if log.count(section) != 1:
        problems.append("%s needs exactly one '%s' section" % (changelog, section))
    if len(line.findall(build)) != 1:
        problems.append("%s needs exactly one 'val appVersion = \"%s\"' line" % (gradle, current))
    if problems:
        sys.exit("\n".join(problems))
elif mode == "release":
    open(changelog, "w", encoding="utf-8").write(log.replace(section, "## %s — %s" % (current, today)))
elif mode == "bump":
    open(gradle, "w", encoding="utf-8").write(line.sub('val appVersion = "%s"' % nxt, build, count=1))
    open(changelog, "w", encoding="utf-8").write(head + "## %s — unreleased\n\n" % nxt + log[len(head):])
PY
}
edit check

# ---- Writes (rolled back on any failure) ----

START=$(git rev-parse HEAD)
TAGGED=0
DONE=0
rollback() {
  local status=$?
  if [ "$DONE" = 1 ] || [ "$status" = 0 ]; then return; fi
  echo "release failed: resetting $BRANCH to $START" >&2
  if [ "$TAGGED" = 1 ]; then git tag -d "$TAG" >/dev/null 2>&1 || true; fi
  git reset -q --hard "$START"
}
trap rollback EXIT

# 1. Release commit
edit release
git add "$CHANGELOG"
git commit -q -m "Release $CURRENT"

# 2. Tag
git tag -a "$TAG" -m "TeleHex $CURRENT"
TAGGED=1

# 3. Start the next version
edit bump
git add "$GRADLE" "$CHANGELOG"
git commit -q -m "Start $NEXT development"
DONE=1

RELEASE=0 . tools/version.sh
echo "Tagged $TAG; $BRANCH is now $VERSION_NAME (versionCode $VERSION_CODE)."
echo "Review, then push both atomically:  git push --atomic $REMOTE $BRANCH $TAG"
