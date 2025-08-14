# Google Maps Link to Waze Android App

This Android application allows you to share Google Maps links and automatically launch Waze for navigation. It extracts coordinates from various Google Maps URL formats and opens Waze with turn-by-turn navigation to the destination.

## Features

- 📱 **Share Integration**: Share Google Maps links directly from Google Maps app
- 📋 **Copy-Paste Support**: Manually paste Google Maps URLs for processing
- 🔄 **Multiple URL Formats**: Supports various Google Maps URL formats including short URLs
- 🗺️ **Smart Coordinate Extraction**: Uses advanced regex patterns and Google Places API
- 🚀 **Direct Waze Launch**: Automatically opens Waze with navigation enabled
- 📍 **Comprehensive Fallbacks**: Multiple methods to ensure successful coordinate extraction

## Supported URL Formats

- Standard Google Maps URLs
- Short URLs (maps.app.goo.gl)
- Place URLs with coordinates
- Protocol Buffer format URLs
- Plus codes and place IDs

## Requirements

- Android 6.0 (API 23) or higher
- Waze app installed
- Internet connection for URL resolution

## Setup for Development

### 1. Clone the Repository

```bash
git clone https://github.com/chigarow/googlemaps-to-waze-app.git
cd maps-to-waze-app
```

### 2. Configure API Keys

The app requires a Google Places API key for enhanced coordinate extraction:

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing one
3. Enable the **Places API**
4. Create credentials (API Key)
5. Copy `gradle.properties.template` to `gradle.properties`
6. Replace `YOUR_GOOGLE_PLACES_API_KEY_HERE` with your actual API key

```bash
cp gradle.properties.template gradle.properties
# Edit gradle.properties and add your API key
```

**⚠️ IMPORTANT: Never commit your `gradle.properties` file with real API keys!**

### 3. Build the App

```bash
./gradlew assembleDebug
```

### 4. Install on Device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Usage

### Method 1: Share from Google Maps
1. Open Google Maps
2. Find your destination
3. Tap "Share"
4. Select "Google Link to Waze"
5. Waze will open with navigation to the destination

### Method 2: Copy-Paste URL
1. Copy a Google Maps URL
2. Open the app
3. Paste the URL in the text field
4. Tap "Open in Waze"
5. Waze will launch with navigation

## Architecture

- **MainActivity.kt**: Handles UI and share intents
- **MapsUrlToWazeUtil.kt**: Core coordinate extraction logic
- **AndroidManifest.xml**: App configuration and intent filters

## API Security

This app uses secure API key management:
- API keys are stored in `gradle.properties` (gitignored)
- Keys are injected at build time via BuildConfig
- No hardcoded secrets in source code

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

Based on [papko26/google-link-to-waze](https://github.com/papko26/google-link-to-waze) with Android native implementation and enhanced coordinate extraction capabilities.

Or open the project in Android Studio and click "Run".

## Project Structure

- `app/` - Main Android application module
- `build.gradle` - Project-level Gradle configuration
- `app/build.gradle` - App module Gradle configuration

## License

This project is licensed under the [MIT License](LICENSE).
