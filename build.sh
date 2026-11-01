#!/usr/bin/env bash
# Build EsimPatch.apk
#
# Requirements: Java (javac/keytool), Android build-tools (d8/aapt2/apksigner),
# an android.jar (API 34), and the compile-time Xposed stubs shipped under stub/.
#
# Usage:
#   ANDROID_JAR=/path/to/android.jar BUILD_TOOLS=/path/to/build-tools ./build.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="${ANDROID_JAR:-/workspace/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-/workspace/android-14}"

D8="$BUILD_TOOLS/d8"
AAPT2="$BUILD_TOOLS/aapt2"
APKSIGNER_JAR="$BUILD_TOOLS/lib/apksigner.jar"

OUT="$ROOT/build"
DIST="$ROOT/dist"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$DIST"

echo "[1/6] javac"
javac --release 8 -classpath "$ANDROID_JAR:$ROOT/stub" -d "$OUT/classes" $(find "$ROOT/src" "$ROOT/stub" -name '*.java')

echo "[2/6] d8"
"$D8" --release --lib "$ANDROID_JAR" --min-api 26 --output "$OUT" $(find "$OUT/classes/io/github/onlymykazari/esimpatch" -name '*.class')

echo "[3/6] aapt2 link"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" --manifest "$ROOT/module/AndroidManifest.xml" --min-sdk-version 26 --target-sdk-version 34 --no-version-vectors

echo "[4/6] assemble"
python3 - "$OUT" "$ROOT" <<'PY'
import sys, zipfile, shutil
out, root = sys.argv[1], sys.argv[2]
shutil.copy(out + "/base.apk", out + "/unsigned.apk")
with zipfile.ZipFile(out + "/unsigned.apk", "a", zipfile.ZIP_DEFLATED) as z:
    z.write(out + "/classes.dex", "classes.dex")
    z.write(root + "/module/assets/xposed_init", "assets/xposed_init")
PY

echo "[5/6] sign"
if [ ! -f "$ROOT/debug.keystore" ]; then
  keytool -genkeypair -v -keystore "$ROOT/debug.keystore" -storepass android -keypass android -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi
java -jar "$APKSIGNER_JAR" sign --ks "$ROOT/debug.keystore" --ks-pass pass:android --key-pass pass:android --out "$DIST/EsimPatch-v1.2.apk" "$OUT/unsigned.apk"

echo "[6/6] verify"
java -jar "$APKSIGNER_JAR" verify --verbose "$DIST/EsimPatch-v1.2.apk" | grep -E "Verifies|v[123]" || true

echo "Done: $DIST/EsimPatch-v1.2.apk"
