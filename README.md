# Download

You can download the latest APK from the [Releases page](https://github.com/chigarow/maps-to-waze-app/releases).


# Google Link to Waze Android App

This Android application is based on [papko26/google-link-to-waze](https://github.com/papko26/google-link-to-waze).
It demonstrates linking Google services to Waze, with additional improvements and customizations.

## Features

- Android app built with Kotlin
- Uses [OkHttp](https://square.github.io/okhttp/) for networking
- Compatible with Android 6.0 (API 23) and above

## Requirements

- Android Studio Hedgehog or newer
- JDK 17+
- Android SDK 34

## Build & Run

Clone the repository and run:

```sh
./gradlew assembleDebug
```

Or open the project in Android Studio and click "Run".

## Project Structure

- `app/` - Main Android application module
- `build.gradle` - Project-level Gradle configuration
- `app/build.gradle` - App module Gradle configuration

## License

This project is licensed under the [MIT License](LICENSE).
