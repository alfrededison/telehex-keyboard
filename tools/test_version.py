"""Checks version numbering against temporary Git histories; needs Python, Bash, and Git."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().with_name("version.sh")


class VersionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="telehex-version-")
        self.addCleanup(self.tmp.cleanup)
        self.repo = Path(self.tmp.name)
        (self.repo / "app").mkdir()
        self.gradle("0.1.0")

    def git(self, *args):
        return subprocess.run(
            ["git", "-c", "user.name=Version Test", "-c", "user.email=version@example.invalid",
             "-c", "commit.gpgsign=false", *args],
            cwd=self.repo, text=True, capture_output=True, check=True,
        ).stdout.strip()

    def init(self):
        self.git("init", "-q", "-b", "main")
        self.commit()

    def gradle(self, version, extra=""):
        (self.repo / "app/build.gradle.kts").write_text(
            'plugins { }\n\nval appVersion = "%s"\n%s\n' % (version, extra)
        )

    def commit(self):
        self.git("add", ".")
        self.git("commit", "-q", "--allow-empty", "-m", "Test history")

    def version(self, release=False):
        env = dict(os.environ, RELEASE="1" if release else "0")
        result = subprocess.run(
            ["bash", "-eu", "-o", "pipefail", "-c",
             '. "$1"; printf "%s\\n%s\\n" "$VERSION_NAME" "$VERSION_CODE"',
             "version-test", str(SCRIPT)],
            cwd=self.repo, env=env, text=True, capture_output=True, check=True,
        )
        name, code = result.stdout.splitlines()
        return name, int(code)

    def test_without_git_starts_at_one(self):
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())

    def test_initial_commit_and_following_commits(self):
        self.init()
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())
        self.commit()
        self.commit()
        self.assertEqual(("0.1.0-dev.3", 100003), self.version())

    def test_version_bump_resets_before_and_after_commit(self):
        self.init()
        self.commit()
        self.gradle("1.0.0")
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version())
        self.commit()
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version())
        self.commit()
        self.assertEqual(("1.0.0-dev.2", 10000002), self.version())

    def test_other_build_file_edits_do_not_reset(self):
        self.init()
        self.gradle("0.1.0", "// minSdk change")
        self.commit()
        self.assertEqual(("0.1.0-dev.2", 100002), self.version())

    def test_tags_do_not_reset_or_change_the_counter(self):
        self.init()
        self.git("tag", "v0.1.0")
        self.gradle("1.0.0")
        self.commit()
        self.commit()
        self.git("tag", "dev")
        self.git("tag", "v1.0.0")
        self.assertEqual(("1.0.0-dev.2", 10000002), self.version())
        self.commit()
        self.assertEqual(("1.0.0-dev.3", 10000003), self.version())

    def test_release_has_no_suffix_and_outranks_its_dev_build(self):
        self.init()
        self.gradle("1.0.0")
        self.commit()
        self.assertEqual(("1.0.0", 10000999), self.version(release=True))
        self.assertGreater(self.version(release=True)[1], self.version()[1])

    def test_adding_build_file_to_existing_repo_starts_at_one(self):
        self.git("init", "-q", "-b", "main")
        (self.repo / "README.md").write_text("Existing repository\n")
        self.git("add", "README.md")
        self.git("commit", "-q", "-m", "Before app")
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())
        self.commit()
        self.assertEqual(("0.1.0-dev.1", 100001), self.version())

    def test_merging_old_version_branch_does_not_choose_its_edit(self):
        self.init()
        old_base = self.git("rev-parse", "HEAD")
        self.gradle("1.0.0")
        self.commit()
        self.commit()
        self.git("checkout", "-q", "-b", "old-version", old_base)
        self.commit()
        self.commit()
        self.gradle("0.1.0", "// old branch edit")
        self.commit()
        self.git("checkout", "-q", "main")
        try:
            self.git("merge", "--no-ff", "--no-commit", "old-version")
        except subprocess.CalledProcessError as error:
            self.assertEqual(1, error.returncode)  # Resolve the build file conflict below.
        self.gradle("1.0.0", "// old branch edit")
        self.commit()
        self.assertEqual(("1.0.0-dev.3", 10000003), self.version())

    def test_version_first_introduced_by_merge_starts_at_one(self):
        self.init()
        self.git("checkout", "-q", "-b", "new-version")
        self.gradle("1.0.0")
        self.commit()
        self.commit()
        self.git("checkout", "-q", "main")
        self.commit()
        self.git("merge", "-q", "--no-ff", "new-version", "-m", "Merge new version")
        self.assertEqual(("1.0.0-dev.1", 10000001), self.version())

    def test_gradle_default_matches_release_numbering(self):
        """app/build.gradle.kts computes the release code itself when VERSION_CODE is unset."""
        text = (SCRIPT.parent.parent / "app/build.gradle.kts").read_text()
        self.assertIn("x * 10_000_000 + y * 100_000 + z * 1_000 + 999", text)


if __name__ == "__main__":
    unittest.main()
