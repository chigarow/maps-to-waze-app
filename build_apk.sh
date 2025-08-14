#!/bin/bash
# Build the APK for the GoogleMapsLinkToWazeApp app
cd "$(dirname "$0")/app"
./gradlew assembleDebug
