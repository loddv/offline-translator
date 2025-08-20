#!/bin/bash

set -eu

if [ "$#" -ne 4 ]; then
    echo "Usage: $0 <keystore_path> <store_password> <key_password> <key_alias>"
    exit 1
fi
if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
    echo "Usage: $0 <keystore_path> <store_password> <key_password> <key_alias>"
    exit 1
fi

set -u

KEYSTORE_PATH="$1"
STORE_PASSWORD="$2"
KEY_PASSWORD="$3"
KEY_ALIAS="$4"

# Find the unsigned APK
if [ -n "${SIGN_DEBUG:-}" ]; then
  echo "Signing debug APK"
  unsigned_apk=$(find app/build/outputs/apk -name "*-debug.apk" | head -1)
else
  echo "Signing release APK"
  unsigned_apk=$(find app/build/outputs/apk -name "*-release-unsigned.apk" | head -1)
fi

if [ -z "$unsigned_apk" ]; then
    echo "No APK found to sign"
    exit 1
fi

VERSION_NAME=$(aapt dump badging "$unsigned_apk"  | grep -oP "versionName='\K(.*?)'" | tr -d "'")
echo "Signing APK: $unsigned_apk"

# Align the APK first (required before signing with apksigner)
aligned_apk="${unsigned_apk%.apk}-aligned.apk"
rm -f "$aligned_apk"
$ANDROID_SDK_ROOT/build-tools/34.0.0/zipalign 4 "$unsigned_apk" "$aligned_apk"

# Sign the APK
mkdir -p signed
signed_fname="signed/translator-$VERSION_NAME.apk"
rm -f "$signed_fname"
$ANDROID_SDK_ROOT/build-tools/34.0.0/apksigner sign \
    --ks "$KEYSTORE_PATH" \
    --ks-pass pass:"$STORE_PASSWORD" \
    --ks-key-alias "$KEY_ALIAS" \
    --key-pass pass:"$KEY_PASSWORD" \
    --out "$signed_fname" \
    "$aligned_apk"

echo "APK successfully signed: $signed_fname"
