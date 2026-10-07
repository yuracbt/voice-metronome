#!/bin/bash
# Manual Android build (no Gradle): aapt2 -> javac -> d8 -> zipalign -> apksigner
set -e
cd "$(dirname "$0")"

SDK=/home/hatch/android-sdk
export JAVA_HOME=/home/hatch/jdk17
export PATH=$JAVA_HOME/bin:$PATH
PLATFORM=$SDK/platforms/android-34/android.jar
BT=$SDK/build-tools/34.0.0
SRC=app/src/main
OUT=build-manual
APP=voice-metronome
KEYSTORE=$OUT/debug.keystore

echo "== cleaning =="
rm -rf $OUT/classes $OUT/dex $OUT/gen $OUT/compiled_res.zip \
       $OUT/app-unsigned.apk $OUT/app-dex.apk $OUT/app-aligned.apk $OUT/$APP.apk
mkdir -p $OUT/classes $OUT/dex $OUT/gen

echo "== aapt2 compile =="
$BT/aapt2 compile --dir $SRC/res -o $OUT/compiled_res.zip

echo "== aapt2 link =="
$BT/aapt2 link -o $OUT/app-unsigned.apk \
  -I $PLATFORM \
  --manifest $SRC/AndroidManifest.xml \
  -A $SRC/assets \
  --java $OUT/gen \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code 6 \
  --version-name 1.5 \
  $OUT/compiled_res.zip

echo "== javac =="
find $SRC/java $OUT/gen -name "*.java" > $OUT/sources.txt
javac -encoding utf-8 -source 8 -target 8 -nowarn -cp "$PLATFORM" -d $OUT/classes @$OUT/sources.txt

echo "== d8 =="
$BT/d8 --lib "$PLATFORM" --min-api 26 --output $OUT/dex $(find $OUT/classes -name "*.class")

echo "== add dex =="
cp $OUT/app-unsigned.apk $OUT/app-dex.apk
(cd $OUT/dex && zip -q ../app-dex.apk classes.dex)

echo "== zipalign =="
$BT/zipalign -f 4 $OUT/app-dex.apk $OUT/app-aligned.apk

echo "== sign =="
$BT/apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out $OUT/$APP.apk $OUT/app-aligned.apk
$BT/apksigner verify --print-certs $OUT/$APP.apk | head -4
ls -la $OUT/$APP.apk
echo "BUILD OK: $OUT/$APP.apk"
