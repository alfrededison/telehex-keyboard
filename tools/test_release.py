"""Runs tools/release.sh in temporary Git repositories; needs Python, Bash, and Git."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


TOOLS = Path(__file__).resolve().parent


class ReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="telehex-release-")
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        (self.repo / "app").mkdir()
        (self.repo / "tools").mkdir()
        for name in ("release.sh", "version.sh"):
            shutil.copy(TOOLS / name, self.repo / "tools" / name)
        (self.repo / "app/build.gradle.kts").write_text('plugins { }\n\nval appVersion = "0.1.0"\n')
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.1.0 — unreleased\n\n- First.\n")
        self.git("init", "-q", "-b", "main")
        self.git("add", ".")
        self.git("commit", "-q", "-m", "Initial")

    def git(self, *args):
        return subprocess.run(
            ["git", "-c", "user.name=Release Test", "-c", "user.email=release@example.invalid",
             "-c", "commit.gpgsign=false", "-c", "tag.gpgsign=false", *args],
            cwd=self.repo, text=True, capture_output=True, check=True,
        ).stdout.strip()

    def release(self, *args):
        env = dict(os.environ, RELEASE_DATE="2026-11-05",
                   GIT_AUTHOR_NAME="Release Test", GIT_AUTHOR_EMAIL="release@example.invalid",
                   GIT_COMMITTER_NAME="Release Test", GIT_COMMITTER_EMAIL="release@example.invalid",
                   GIT_CONFIG_COUNT="2", GIT_CONFIG_KEY_0="commit.gpgsign", GIT_CONFIG_VALUE_0="false",
                   GIT_CONFIG_KEY_1="tag.gpgsign", GIT_CONFIG_VALUE_1="false")
        return subprocess.run(["bash", "tools/release.sh", *args], cwd=self.repo, env=env,
                              text=True, capture_output=True)

    def version(self, release):
        result = subprocess.run(
            ["bash", "-eu", "-c", '. tools/version.sh; printf "%s\\n%s\\n" "$VERSION_NAME" "$VERSION_CODE"'],
            cwd=self.repo, env=dict(os.environ, RELEASE="1" if release else "0"),
            text=True, capture_output=True, check=True)
        name, code = result.stdout.splitlines()
        return name, int(code)

    def show(self, rev, path):
        return self.git("show", "%s:%s" % (rev, path))

    def test_tags_release_and_bumps_to_next_dev_version(self):
        result = self.release()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["Start 0.1.1 development", "Release 0.1.0", "Initial"],
                         self.git("log", "--format=%s").splitlines())
        self.assertIn('val appVersion = "0.1.0"', self.show("v0.1.0", "app/build.gradle.kts"))
        self.assertIn("## 0.1.0 — 2026-11-05", self.show("v0.1.0", "CHANGELOG.md"))
        self.assertEqual("TeleHex 0.1.0", self.git("tag", "-l", "--format=%(contents:subject)", "v0.1.0"))
        changelog = (self.repo / "CHANGELOG.md").read_text()
        self.assertTrue(changelog.startswith("# Changelog\n\n## 0.1.1 — unreleased\n\n## 0.1.0 — 2026-11-05"))
        self.assertEqual(("0.1.1-dev.1", 101001), self.version(release=False))

    def test_next_dev_build_outranks_the_release(self):
        released = self.version(release=True)  # 0.1.0 before releasing
        self.assertEqual(0, self.release().returncode)
        self.assertGreater(self.version(release=False)[1], released[1])

    def test_minor_and_major(self):
        self.assertEqual(0, self.release("minor").returncode)
        self.assertEqual(("0.2.0-dev.1", 200001), self.version(release=False))
        self.assertEqual(0, self.release("major").returncode)  # needs "## 0.2.0 — unreleased", added above
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version(release=False))

    def test_refuses_dirty_tree_missing_changelog_section_and_existing_tag(self):
        (self.repo / "dirty.txt").write_text("x")
        self.assertNotEqual(0, self.release().returncode)
        (self.repo / "dirty.txt").unlink()
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.0.9 — 2026-01-01\n")
        self.git("commit", "-q", "-am", "Drop section")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("unreleased", result.stderr)
        (self.repo / "CHANGELOG.md").write_text("# Changelog\n\n## 0.1.0 — unreleased\n")
        self.git("commit", "-q", "-am", "Restore section")
        self.git("tag", "v0.1.0")
        result = self.release()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("already exists", result.stderr)
        self.assertEqual("", self.git("status", "--porcelain"))


if __name__ == "__main__":
    unittest.main()
