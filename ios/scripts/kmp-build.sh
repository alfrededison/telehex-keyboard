#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/../.."
if [[ ! -x "${JAVA_HOME:-}/bin/java" ]]; then
  export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  if [[ ! -x "${JAVA_HOME:-}/bin/java" ]]; then
    export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
  fi
fi
./gradlew -PskipAndroid=true :telex:embedAndSignAppleFrameworkForXcode
