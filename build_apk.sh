#!/bin/bash
# Build the APK for the GoogleLinkToWaze app
cd "$(dirname "$0")/app"
./gradlew assembleDebug
