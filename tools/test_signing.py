"""Checks signing guards without private keys or network access."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).resolve().with_name("signing.sh")
PIN = Path(__file__).resolve().parent.parent / "signing" / "release-cert.sha256"
NAMES = ("ANDROID_KEYSTORE_B64", "ANDROID_KEYSTORE_PASSWORD", "ANDROID_KEY_ALIAS", "ANDROID_KEY_PASSWORD")


class SigningTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(prefix="telehex-signing-")
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.pin = self.root / "certificate.sha256"
        self.pin.write_text("a" * 64 + "\n")
        self.env = dict(os.environ)
        for name in NAMES:
            self.env[name] = "dummy-value-must-not-appear-in-errors"

    def run_check(self, function, *args, env=None):
        return subprocess.run(
            ["bash", "-eu", "-o", "pipefail", "-c", '. "$1"; shift; "$@"',
             "signing-test", str(SCRIPT), function, *map(str, args)],
            env=self.env if env is None else env, text=True, capture_output=True,
        )

    def test_committed_pin_is_valid(self):
        self.assertRegex(PIN.read_text().strip(), r"^[0-9a-f]{64}$")

    def test_each_missing_secret_stops_without_printing_values(self):
        for name in NAMES:
            with self.subTest(name=name):
                env = dict(self.env)
                env.pop(name)
                result = self.run_check("require_project_signing", self.pin, env=env)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(name, result.stderr)
                self.assertNotIn("dummy-value", result.stdout + result.stderr)

    def test_complete_configuration_requires_a_valid_pin(self):
        self.assertEqual(0, self.run_check("require_project_signing", self.pin).returncode)
        self.pin.write_text("not-a-certificate-pin\n")
        self.assertNotEqual(0, self.run_check("require_project_signing", self.pin).returncode)
        self.pin.unlink()
        self.assertNotEqual(0, self.run_check("require_project_signing", self.pin).returncode)

    def signer(self, fingerprint, status=0):
        tool = self.root / "apksigner"
        tool.write_text("#!/bin/sh\nprintf '%s\\n' 'Signer #1 certificate SHA-256 digest: "
                        + fingerprint + "'\nexit " + str(status) + "\n")
        tool.chmod(0o700)
        return tool

    def test_certificate_must_match_the_pin(self):
        tool = self.signer("a" * 64)
        self.assertEqual(0, self.run_check("verify_project_certificate", "app.apk", tool, self.pin).returncode)
        tool = self.signer("b" * 64)
        result = self.run_check("verify_project_certificate", "app.apk", tool, self.pin)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("does not match", result.stderr)

    def test_failed_apk_verification_is_not_accepted(self):
        tool = self.signer("a" * 64, status=1)
        self.assertNotEqual(0, self.run_check("verify_project_certificate", "app.apk", tool, self.pin).returncode)

    def bundle_tools(self, fingerprint, verified="jar verified.", status=0):
        """Fake keytool and jarsigner printing what the real ones print for a signed bundle."""
        keytool = self.root / "keytool"
        colons = ":".join(fingerprint[i:i + 2] for i in range(0, len(fingerprint), 2)).upper()
        keytool.write_text("#!/bin/sh\nprintf '%s\\n' 'Certificate fingerprints:' "
                           "'\t SHA1: 00:11' '\t SHA256: " + colons + "'\n")
        jarsigner = self.root / "jarsigner"
        jarsigner.write_text("#!/bin/sh\nprintf '%s\\n' '' '" + verified + "'\nexit " + str(status) + "\n")
        for tool in (keytool, jarsigner):
            tool.chmod(0o700)
        return keytool, jarsigner

    def test_bundle_certificate_must_match_the_pin(self):
        keytool, jarsigner = self.bundle_tools("a" * 64)
        self.assertEqual(0, self.run_check("verify_bundle_certificate", "app.aab", keytool, jarsigner, self.pin).returncode)
        keytool, jarsigner = self.bundle_tools("b" * 64)
        result = self.run_check("verify_bundle_certificate", "app.aab", keytool, jarsigner, self.pin)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("does not match", result.stderr)

    def test_unsigned_or_broken_bundle_is_not_accepted(self):
        keytool, jarsigner = self.bundle_tools("a" * 64, verified="jar is unsigned.")
        result = self.run_check("verify_bundle_certificate", "app.aab", keytool, jarsigner, self.pin)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("not signed", result.stderr)
        keytool, jarsigner = self.bundle_tools("a" * 64, status=1)
        self.assertNotEqual(0, self.run_check("verify_bundle_certificate", "app.aab", keytool, jarsigner, self.pin).returncode)


if __name__ == "__main__":
    unittest.main()
