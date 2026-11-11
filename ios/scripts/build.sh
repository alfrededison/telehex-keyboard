#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
xcodegen generate --spec project.json
xcodebuild -project TeleHex.xcodeproj -scheme TeleHex \
  -configuration "${CONFIGURATION:-Debug}" -sdk "${SDK:-iphonesimulator}" \
  -destination "${DESTINATION:-generic/platform=iOS Simulator}" \
  -derivedDataPath ../out/ios build CODE_SIGNING_ALLOWED=NO
