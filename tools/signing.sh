#!/usr/bin/env bash
# Shared signing checks. The caller supplies the certificate pin file and uses set -e.

require_project_signing() {
  local name expected
  for name in ANDROID_KEYSTORE_B64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
    if [ -z "${!name:-}" ]; then
      echo "Missing project signing secret: $name" >&2
      return 1
    fi
  done
  if [ ! -f "$1" ]; then
    echo "Missing project signing certificate pin: $1" >&2
    return 1
  fi
  expected=$(tr -d '\r\n' < "$1")
  if [[ ! "$expected" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Invalid project signing certificate pin: $1" >&2
    return 1
  fi
}

verify_project_certificate() {
  local expected actual certificates
  expected=$(tr -d '\r\n' < "$3")
  certificates=$("$2" verify --min-sdk-version 26 --print-certs "$1") || return 1
  actual=$(printf '%s\n' "$certificates" | awk '/^Signer #1 certificate SHA-256 digest: / {print $NF}')
  if [ "$actual" != "$expected" ]; then
    echo "APK signing certificate does not match the project certificate pin" >&2
    return 1
  fi
}

# An app bundle carries a JAR signature, which apksigner cannot read: jarsigner checks it, keytool
# prints the certificate. Arguments: bundle, keytool, jarsigner, certificate pin.
verify_bundle_certificate() {
  local expected actual verified certificate
  expected=$(tr -d '\r\n' < "$4")
  verified=$("$3" -verify "$1") || return 1
  if ! printf '%s\n' "$verified" | grep -qx 'jar verified.'; then
    echo "App bundle is not signed" >&2
    return 1
  fi
  certificate=$("$2" -printcert -jarfile "$1") || return 1
  actual=$(printf '%s\n' "$certificate" | awk '$1 == "SHA256:" {print $2; exit}' | tr -d ':' | tr 'A-F' 'a-f')
  if [ "$actual" != "$expected" ]; then
    echo "App bundle signing certificate does not match the project certificate pin" >&2
    return 1
  fi
}
