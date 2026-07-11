#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build"
ANDROID_RES="/system/framework/framework-res.apk"
ANDROID_CLASSES="/data/data/com.termux/files/usr/share/java/android.jar"
PKG="com.daidong.hermesstandalone"
KEYSTORE="$ROOT/hermes-standalone-debug.keystore"
UNSIGNED="$BUILD/hermes-standalone-unsigned.apk"
ALIGNED="$BUILD/hermes-standalone-aligned.apk"
SIGNED="$BUILD/hermes-standalone-poc.apk"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$BUILD/dex" "$BUILD/compiled" "$BUILD/apk"

# Compile Android resources.
aapt2 compile --dir "$ROOT/res" -o "$BUILD/compiled/resources.zip"

# Link manifest + resources into an unsigned APK shell.
ASSET_ARGS=()
if [ -d "$ROOT/assets" ]; then
  ASSET_ARGS=(-A "$ROOT/assets")
fi
aapt2 link \
  -I "$ANDROID_RES" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -o "$UNSIGNED" \
  --java "$BUILD/generated" \
  "${ASSET_ARGS[@]}" \
  "$BUILD/compiled/resources.zip" \
  --min-sdk-version 23 \
  --target-sdk-version 28

# Compile Java. ecj accepts sourcepath/classpath and writes .class files.
SHIZUKU_JAR="$ROOT/libs/shizuku-api.jar"
SHIZUKU_PROVIDER_JAR="$ROOT/libs/shizuku-provider.jar"
SHIZUKU_AIDL_JAR="$ROOT/libs/shizuku-aidl.jar"
SHIZUKU_SHARED_JAR="$ROOT/libs/shizuku-shared.jar"
ecj \
  -bootclasspath "$ANDROID_CLASSES" \
  -classpath "$ANDROID_CLASSES:$BUILD/generated:$SHIZUKU_JAR:$SHIZUKU_PROVIDER_JAR:$SHIZUKU_AIDL_JAR:$SHIZUKU_SHARED_JAR" \
  -sourcepath "$ROOT/src:$BUILD/generated" \
  -d "$BUILD/classes" \
  $(find "$ROOT/src" "$BUILD/generated" -name '*.java' | tr '\n' ' ')

# Convert class files to classes.dex.
d8 --min-api 23 --lib "$ANDROID_CLASSES" --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class' | tr '\n' ' ') \
  "$SHIZUKU_JAR" \
  "$SHIZUKU_PROVIDER_JAR" \
  "$SHIZUKU_AIDL_JAR" \
  "$SHIZUKU_SHARED_JAR"

# Add dex to APK.
cp "$UNSIGNED" "$BUILD/apk/base.apk"
(
  cd "$BUILD/dex"
  zip -q -u "$BUILD/apk/base.apk" classes.dex
)

# Align.
zipalign -f 4 "$BUILD/apk/base.apk" "$ALIGNED"

# Create debug signing key once.
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair \
    -keystore "$KEYSTORE" \
    -storepass android \
    -keypass android \
    -alias hermesdebug \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Hermes Standalone Debug,O=Daidong,C=VN"
fi

# Sign.
apksigner sign \
  --ks "$KEYSTORE" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$SIGNED" \
  "$ALIGNED"

apksigner verify --verbose "$SIGNED"
echo "$SIGNED"
