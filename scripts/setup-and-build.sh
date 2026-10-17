#!/usr/bin/env bash
# EducationVault: check prerequisites, run the tests, build the debug APK when possible.
# Safe to re-run: it never deletes or overwrites your files.
#
# Run from the repository root:   ./scripts/setup-and-build.sh
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

FAILED=0
say()  { printf '\n==> %s\n' "$*"; }
ok()   { printf '  [OK] %s\n' "$*"; }
warn() { printf '  [!]  %s\n' "$*"; }
bad()  { printf '  [X]  %s\n' "$*" >&2; FAILED=1; }

java_major() { java -version 2>&1 | head -n1 | sed -E 's/^[^"]*"([0-9]+).*/\1/'; }

say "1/5  Checking prerequisites"
if command -v git >/dev/null 2>&1; then ok "git"; else bad "git is missing"; fi

if command -v node >/dev/null 2>&1; then
  NODE_MAJOR=$(node -p 'process.versions.node.split(".")[0]')
  if [ "$NODE_MAJOR" -ge 18 ]; then ok "node $(node -v)"; else bad "Node 18+ is required (found $(node -v))"; fi
else
  bad "node is missing (needed for the backend tests)"
fi

CAN_BUILD_ANDROID=1
if command -v java >/dev/null 2>&1; then
  JM=$(java_major)
  if [ "${JM:-0}" -ge 17 ] 2>/dev/null; then ok "java $JM"; else warn "Java 17+ is needed for the Android build (found ${JM:-unknown})"; CAN_BUILD_ANDROID=0; fi
else
  warn "java is missing"; CAN_BUILD_ANDROID=0
fi

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK" ] && [ -d "$HOME/Android/Sdk" ]; then SDK="$HOME/Android/Sdk"; fi
if [ -n "$SDK" ] && [ -d "$SDK" ]; then
  ok "Android SDK: $SDK"; export ANDROID_HOME="$SDK"
else
  warn "Android SDK not found (normal inside Codespaces)"; CAN_BUILD_ANDROID=0
fi

GRADLE=""
if [ -x ./gradlew ] && [ -f gradle/wrapper/gradle-wrapper.jar ]; then
  GRADLE="./gradlew"; ok "Gradle wrapper"
elif command -v gradle >/dev/null 2>&1; then
  GRADLE="gradle"; ok "gradle $(gradle --version 2>/dev/null | awk '/^Gradle/{print $2; exit}')"
else
  warn "gradle not found"; CAN_BUILD_ANDROID=0
fi

say "2/5  Checking the repository and configuration"
for f in settings.gradle.kts build.gradle.kts app/build.gradle.kts app-config.properties \
         app/src/main/AndroidManifest.xml .github/workflows/android-build.yml backend/apps-script/appsscript.json; do
  if [ -f "$f" ]; then ok "$f"; else bad "missing file: $f"; fi
done
if [ -f app-config.properties ]; then
  if grep -v '^[[:space:]]*#' app-config.properties | grep -q 'REPLACE_ME'; then
    warn "app-config.properties still has REPLACE_ME values: the app will show a configuration error screen"
  else
    ok "app-config.properties has no placeholders"
  fi
fi
if ls client_secret*.json >/dev/null 2>&1; then
  bad "A client_secret*.json file is in this folder. Delete it; it must never be committed."
fi

say "3/5  Backend unit tests (Node, mocked Drive and Firebase)"
if [ -d backend/apps-script ] && command -v node >/dev/null 2>&1; then
  if (cd backend/apps-script && npm test --silent 2>&1 | tail -n 9); then ok "backend tests passed"; else bad "backend tests FAILED"; fi
else
  bad "could not run backend tests"
fi

say "4/5  Android unit tests and debug APK"
APK="app/build/outputs/apk/debug/app-debug.apk"
APK_BUILT=0
if [ "$CAN_BUILD_ANDROID" -eq 1 ]; then
  if $GRADLE --no-daemon :app:testDebugUnitTest :app:assembleDebug; then
    if [ -f "$APK" ]; then APK_BUILT=1; ok "APK built"; else bad "build finished but $APK was not found"; fi
  else
    bad "Android build FAILED (see the messages above)"
  fi
else
  warn "Skipping the local Android build: Java 17, the Android SDK and Gradle are not all available here."
  warn "This is fine. Push to GitHub and let GitHub Actions build the APK."
fi

say "5/5  Summary"
if [ "$APK_BUILT" -eq 1 ]; then
  echo "  Debug APK: $(pwd)/$APK"
else
  echo "  No APK was built locally. Download it from GitHub: Actions tab > latest run > Artifacts > EducationVault-debug-apk"
fi
if [ "$FAILED" -ne 0 ]; then echo "  RESULT: FAILED (see [X] lines above)"; exit 1; fi
echo "  RESULT: OK"
