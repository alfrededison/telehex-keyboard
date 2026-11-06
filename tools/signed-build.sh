#!/usr/bin/env bash
# Builds out/TeleHex.apk signed with the project key kept in .signing/ (gitignored), so it installs
# over CI's dev and release builds. Extra env (e.g. RELEASE=1) is passed through to release-build.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
exec python3 - "$@" <<'EOF'
import base64, json, os, sys
from pathlib import Path
private = Path(".signing")
config = json.loads((private / "credentials.json").read_text())
env = dict(os.environ,
    REQUIRE_PROJECT_SIGNING="1",
    ANDROID_KEYSTORE_B64=base64.b64encode((private / config["keystore_file"]).read_bytes()).decode(),
    ANDROID_KEYSTORE_PASSWORD=config["store_password"],
    ANDROID_KEY_ALIAS=config["alias"],
    ANDROID_KEY_PASSWORD=config["key_password"])
os.execvpe("tools/release-build.sh", ["tools/release-build.sh", *sys.argv[1:]], env)
EOF
