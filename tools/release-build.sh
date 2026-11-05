#!/usr/bin/env bash
# Builds out/TeleHex.apk (minified release build) and out/TeleHex.apk.sha256.
#
#   tools/release-build.sh              -> X.Y.Z-dev.N
#   RELEASE=1 tools/release-build.sh    -> X.Y.Z
#
# Signing: set ANDROID_KEYSTORE_B64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and
# ANDROID_KEY_PASSWORD to sign with the project key; otherwise out/debug.keystore is generated.
# RELEASE=1 or REQUIRE_PROJECT_SIGNING=1 requires the project key and the certificate pin in
# signing/release-cert.sha256. An APK signed with another key cannot be installed over an
# existing install: Android refuses the update.
#
# Toolchain: ANDROID_SDK (default $ANDROID_SDK_ROOT, $ANDROID_HOME, ~/Library/Android/sdk or
# ~/Android/Sdk) and BUILD_TOOLS (default: newest installed) for apksigner/keytool checks.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$PWD"

. tools/signing.sh
PROJECT_SIGNING=0
if [ "${RELEASE:-0}" = 1 ] || [ "${REQUIRE_PROJECT_SIGNING:-0}" = 1 ] || [ -n "${ANDROID_KEYSTORE_B64:-}" ]; then
  require_project_signing "$ROOT/signing/release-cert.sha256"
  PROJECT_SIGNING=1
fi

for _sdk in "${ANDROID_SDK:-}" "${ANDROID_SDK_ROOT:-}" "${ANDROID_HOME:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
  if [ -n "$_sdk" ] && [ -d "$_sdk/build-tools" ]; then ANDROID_SDK="$_sdk"; break; fi
done
: "${ANDROID_SDK:?Android SDK not found: set ANDROID_SDK}"
: "${BUILD_TOOLS:=$(ls -1 "$ANDROID_SDK/build-tools" | sort -V | tail -n1)}"
BT="$ANDROID_SDK/build-tools/$BUILD_TOOLS"
[ -x "$BT/apksigner" ] || { echo "missing $BT/apksigner" >&2; exit 1; }
[ -f local.properties ] || echo "sdk.dir=$ANDROID_SDK" > local.properties

# shellcheck source=tools/version.sh
. tools/version.sh
echo "building $VERSION_NAME (versionCode $VERSION_CODE)"

mkdir -p out
rm -f out/TeleHex.apk out/TeleHex.apk.sha256 out/TeleHex.apk.idsig
KS=
cleanup() {
  local status=$?
  if [ -n "$KS" ]; then rm -f "$KS"; fi
  # A failed build (for example a certificate that does not match the pin) must leave no APK behind.
  if [ "$status" != 0 ]; then rm -f out/TeleHex.apk out/TeleHex.apk.sha256; fi
}
trap cleanup EXIT
if [ "$PROJECT_SIGNING" = 1 ]; then
  KS=$(mktemp "${RUNNER_TEMP:-$ROOT/out}/telehex-signing.XXXXXX")
  chmod 600 "$KS"
  printf '%s' "$ANDROID_KEYSTORE_B64" | base64 -d > "$KS"
  export ANDROID_KEYSTORE_PATH="$KS"
else
  unset ANDROID_KEYSTORE_PATH
fi

./gradlew --no-daemon -q :telex:test :app:assembleRelease

if [ "$PROJECT_SIGNING" = 1 ]; then
  cp app/build/outputs/apk/release/app-release.apk out/TeleHex.apk
else
  DEBUG_KS=out/debug.keystore
  [ -e "$DEBUG_KS" ] || keytool -genkeypair -keystore "$DEBUG_KS" -storetype pkcs12 -alias telehex-debug \
    -keyalg RSA -keysize 2048 -validity 10000 -storepass android -keypass android -dname "CN=TeleHex debug" >/dev/null 2>&1
  "$BT/apksigner" sign --ks "$DEBUG_KS" --ks-pass pass:android --key-pass pass:android --v4-signing-enabled false \
    --out out/TeleHex.apk app/build/outputs/apk/release/app-release-unsigned.apk
fi

"$BT/apksigner" verify --min-sdk-version 26 out/TeleHex.apk
if [ "$PROJECT_SIGNING" = 1 ]; then
  verify_project_certificate out/TeleHex.apk "$BT/apksigner" "$ROOT/signing/release-cert.sha256"
  echo "Project signing certificate verified"
fi
if command -v sha256sum >/dev/null; then
  (cd out && sha256sum TeleHex.apk > TeleHex.apk.sha256)
else
  (cd out && shasum -a 256 TeleHex.apk > TeleHex.apk.sha256)
fi
echo "BUILD OK: $ROOT/out/TeleHex.apk ($VERSION_NAME, versionCode $VERSION_CODE)"
