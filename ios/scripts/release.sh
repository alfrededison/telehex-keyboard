#!/bin/bash
# Archive bản iOS và tải lên App Store Connect (TestFlight). Chạy: ios/scripts/release.sh
# Cần ios/Config/Signing.local.xcconfig (không commit) với `DEVELOPMENT_TEAM = <Team ID>`, và một trong hai cách xác thực:
#   - khoá API App Store Connect: thêm ASC_KEY_PATH, ASC_KEY_ID, ASC_ISSUER_ID vào file đó (hoặc đặt biến môi trường cùng tên).
#     Ký thủ công: scripts/asc_signing.py tự tạo chứng chỉ phân phối và profile App Store khi cần.
#   - tài khoản thuộc team, có quyền Certificates, Identifiers & Profiles, đăng nhập trong Xcode › Settings › Accounts.
#     Ký tự động như bình thường.
# DESTINATION=export chỉ xuất .ipa vào out/ios-release, không tải lên.
set -euo pipefail
cd "$(dirname "$0")/.."
# Giá trị cuối cùng của một khoá trong Signing.local.xcconfig, bỏ chú thích // cuối dòng; thiếu file thì rỗng.
setting() { sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" Config/Signing.local.xcconfig 2>/dev/null | tail -1 | sed -e 's:[[:space:]]//.*$::' -e 's/[[:space:]]*$//' || true; }
team=$(setting DEVELOPMENT_TEAM)
[ -n "$team" ] || { echo "Thiếu DEVELOPMENT_TEAM trong ios/Config/Signing.local.xcconfig" >&2; exit 1; }
ASC_KEY_PATH=${ASC_KEY_PATH:-$(setting ASC_KEY_PATH)}
ASC_KEY_ID=${ASC_KEY_ID:-$(setting ASC_KEY_ID)}
ASC_ISSUER_ID=${ASC_ISSUER_ID:-$(setting ASC_ISSUER_ID)}
ASC_KEY_PATH=${ASC_KEY_PATH/#\~/$HOME}
out=../out/ios-release
archive="$out/TeleHex.xcarchive"
mkdir -p "$out"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
options="$work/ExportOptions.plist"

xcodegen generate --spec project.json
auth=() signing=() style=automatic profiles="" identity=""
if [ -n "$ASC_KEY_PATH" ]; then
  [ -n "$ASC_KEY_ID" ] && [ -n "$ASC_ISSUER_ID" ] || { echo "Thiếu ASC_KEY_ID hoặc ASC_ISSUER_ID" >&2; exit 1; }
  auth=(-authenticationKeyPath "$ASC_KEY_PATH" -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
  # Khoá API không quản lý được App Group nên ký tự động bằng khoá lỗi 401; dùng profile App Store tạo qua API.
  python3 scripts/asc_signing.py project.json "$team" "$ASC_KEY_PATH" "$ASC_KEY_ID" "$ASC_ISSUER_ID" > "$work/profiles"
  # Dòng đầu `identity <SHA-1>`: ký bằng đúng chứng chỉ đó, không theo tên (Keychain có thể có nhiều chứng chỉ cùng tên).
  identity=$(awk '$1 == "identity" { print $2 }' "$work/profiles")
  {
    echo "CODE_SIGN_STYLE = Manual"
    echo "CODE_SIGN_IDENTITY = $identity"
    echo "DEVELOPMENT_TEAM = $team"
    echo 'PROVISIONING_PROFILE_SPECIFIER = $(TELEHEX_PROFILE_$(TARGET_NAME))'
    awk '$1 != "identity" { print "TELEHEX_PROFILE_" $1 " = " $3 }' "$work/profiles"
  } > "$work/signing.xcconfig"
  signing=(-xcconfig "$work/signing.xcconfig")
  style=manual
  profiles=$(awk '$1 != "identity" { printf "<key>%s</key><string>%s</string>", $2, $3 }' "$work/profiles")
fi

rm -rf "$archive"
xcodebuild -project TeleHex.xcodeproj -scheme TeleHex -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$archive" archive \
  ${signing[@]+"${signing[@]}"} -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}

manual=""
[ "$style" = manual ] && manual="<key>signingCertificate</key><string>$identity</string>
  <key>provisioningProfiles</key><dict>$profiles</dict>"
cat > "$options" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>method</key><string>app-store-connect</string>
  <key>destination</key><string>${DESTINATION:-upload}</string>
  <key>teamID</key><string>$team</string>
  <key>signingStyle</key><string>$style</string>
  $manual
  <key>manageAppVersionAndBuildNumber</key><true/>
  <key>uploadSymbols</key><true/>
</dict>
</plist>
PLIST
xcodebuild -exportArchive -archivePath "$archive" -exportOptionsPlist "$options" -exportPath "$out" \
  -allowProvisioningUpdates ${auth[@]+"${auth[@]}"}
echo "Xong: $out"
