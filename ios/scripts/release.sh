#!/bin/bash
# Archive bản iOS và tải lên App Store Connect (TestFlight).
# Cần: ios/Config/Signing.local.xcconfig có `DEVELOPMENT_TEAM = <Team ID>`, và một trong hai cách xác thực:
#   - tài khoản thuộc team đã đăng nhập trong Xcode › Settings › Accounts, hoặc
#   - khoá API App Store Connect: ASC_KEY_PATH, ASC_KEY_ID, ASC_ISSUER_ID.
# DESTINATION=export chỉ xuất .ipa vào out/ios-release, không tải lên.
set -euo pipefail
cd "$(dirname "$0")/.."
team=$(sed -n 's/^[[:space:]]*DEVELOPMENT_TEAM[[:space:]]*=[[:space:]]*//p' Config/Signing.local.xcconfig 2>/dev/null | tr -d '[:space:]')
[ -n "$team" ] || { echo "Thiếu DEVELOPMENT_TEAM trong ios/Config/Signing.local.xcconfig" >&2; exit 1; }
out=../out/ios-release
archive="$out/TeleHex.xcarchive"
auth=()
if [ -n "${ASC_KEY_PATH:-}" ]; then
  auth=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
fi
xcodegen generate --spec project.json
rm -rf "$archive"
xcodebuild -project TeleHex.xcodeproj -scheme TeleHex -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$archive" archive \
  -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
options="$work/ExportOptions.plist"
cat > "$options" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>${DESTINATION:-upload}</string>
  <key>teamID</key><string>$team</string>
  <key>signingStyle</key><string>automatic</string>
  <key>manageAppVersionAndBuildNumber</key><true/>
  <key>uploadSymbols</key><true/>
</dict>
</plist>
PLIST
xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$options" -exportPath "$out" \
  -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}
echo "Xong: $out"
