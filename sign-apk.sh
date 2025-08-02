#!/bin/bash

set -eu

if [ "$#" -ne 5 ]; then
    echo "Usage: $0 <keystore_path> <store_password> <key_password> <key_alias> <version_name>"
    exit 1
fi
if [ -z "$1" ] || [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ] || [ -z "$5" ]; then
    echo "Usage: $0 <keystore_path> <store_password> <key_password> <key_alias> <version_name>"
    exit 1
fi

set -u

KEYSTORE_PATH="$1"
STORE_PASSWORD="$2"
KEY_PASSWORD="$3"
KEY_ALIAS="$4"
VERSION_NAME="$5"

# Find the unsigned APK
unsigned_apk=$(find app/build/outputs/apk -name "*-release-unsigned.apk" | head -1)
if [ -z "$unsigned_apk" ]; then
    echo "No APK found to sign"
    exit 1
fi

echo "Signing APK: $unsigned_apk"

# Sign the APK
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    "$unsigned_apk" \
    "$KEY_ALIAS"

# Align the APK
mkdir -p signed
rm -f "signed/app-release-signed-$VERSION_NAME.apk"
$ANDROID_SDK_ROOT/build-tools/34.0.0/zipalign -v 4 "$unsigned_apk" "signed/app-release-signed-$VERSION_NAME.apk"

# Verify the signature
jarsigner -verify -verbose -certs "signed/app-release-signed-$VERSION_NAME.apk"

echo "APK successfully signed: signed/app-release-signed-$VERSION_NAME.apk"
