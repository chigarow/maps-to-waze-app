package com.example.googlelinktowaze

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "GoogleMapsToWaze"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate() started")
        
        try {
            setContentView(R.layout.activity_main)
            Log.d(TAG, "setContentView completed")

            val mapsUrlEditText = findViewById<EditText>(R.id.mapsUrlEditText)
            val openInWazeButton = findViewById<Button>(R.id.openInWazeButton)
            Log.d(TAG, "UI elements found successfully")

            // Handle manual input
            openInWazeButton.setOnClickListener {
                Log.d(TAG, "Open in Waze button clicked")
                val url = mapsUrlEditText.text.toString()
                Log.d(TAG, "Input URL: $url")
                
                if (url.isBlank()) {
                    Log.w(TAG, "URL is blank")
                    Toast.makeText(this, "Please enter a Google Maps URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                
                Log.d(TAG, "Launching coroutine to handle Google Maps URL")
                lifecycleScope.launch {
                    handleGoogleMapsUrl(url)
                }
            }

            // Handle incoming share intent from Google Maps
            Log.d(TAG, "Checking for share intent")
            if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                Log.d(TAG, "Share intent detected")
                val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                Log.d(TAG, "Shared URL: $sharedUrl")
                
                if (!sharedUrl.isNullOrBlank()) {
                    Log.d(TAG, "Setting shared URL in EditText and processing")
                    mapsUrlEditText.setText(sharedUrl)
                    lifecycleScope.launch {
                        handleGoogleMapsUrl(sharedUrl)
                    }
                } else {
                    Log.w(TAG, "Shared URL is null or blank")
                }
            } else {
                Log.d(TAG, "No share intent detected")
            }
            
            Log.d(TAG, "onCreate() completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate()", e)
            Toast.makeText(this, "Error initializing app: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Extract coordinates and launch Waze
    private suspend fun handleGoogleMapsUrl(url: String) {
        Log.d(TAG, "handleGoogleMapsUrl() started with URL: $url")
        
        try {
            Log.d(TAG, "Calling MapsUrlToWazeUtil.extractCoordinatesFromUrl()")
            val coords = MapsUrlToWazeUtil.extractCoordinatesFromUrl(url)
            Log.d(TAG, "Extracted coordinates: $coords")
            
            if (coords != null) {
                val (lat, lon) = coords
                Log.d(TAG, "Coordinates found - Lat: $lat, Lon: $lon")
                
                val wazeUri = "waze://?ll=$lat,$lon&navigate=yes"
                Log.d(TAG, "Generated Waze URI: $wazeUri")
                
                try {
                    // Try to launch Waze directly with multiple fallback approaches
                    if (launchWaze(wazeUri, lat, lon)) {
                        Log.d(TAG, "Waze launched successfully")
                    } else {
                        Log.w(TAG, "Failed to launch Waze, trying alternative methods")
                        if (!tryAlternativeWazeLaunch(lat, lon)) {
                            Log.w(TAG, "All Waze launch methods failed, opening Play Store")
                            openWazeInPlayStore()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error launching Waze app", e)
                    Toast.makeText(this, "Failed to open Waze app: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "Could not extract coordinates from URL")
                Toast.makeText(this, "Could not extract coordinates from the URL.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleGoogleMapsUrl()", e)
            Toast.makeText(this, "Error processing URL: ${e.message}", Toast.LENGTH_LONG).show()
        }
        
        Log.d(TAG, "handleGoogleMapsUrl() completed")
    }

    private fun launchWaze(wazeUri: String, lat: Double, lon: Double): Boolean {
        return try {
            // Method 1: Direct URI launch with explicit package
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(wazeUri))
            intent.setPackage("com.waze")
            Log.d(TAG, "Attempting to launch Waze with package: $wazeUri")
            startActivity(intent)
            Log.d(TAG, "Waze launched with explicit package")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct package launch failed: ${e.message}")
            try {
                // Method 2: Try without explicit package
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(wazeUri))
                Log.d(TAG, "Attempting to launch Waze without package constraint")
                startActivity(intent)
                Log.d(TAG, "Waze launched without package constraint")
                true
            } catch (e2: Exception) {
                Log.w(TAG, "URI launch failed: ${e2.message}")
                false
            }
        }
    }

    private fun tryAlternativeWazeLaunch(lat: Double, lon: Double): Boolean {
        return try {
            // Method 3: Try alternative Waze URI formats
            val alternativeUris = listOf(
                "waze://?ll=$lat,$lon&navigate=yes&z=10",
                "https://waze.com/ul?ll=$lat,$lon&navigate=yes",
                "waze://?q=$lat,$lon&navigate=yes",
                "geo:$lat,$lon?q=$lat,$lon(Destination)"
            )

            for (uri in alternativeUris) {
                try {
                    Log.d(TAG, "Trying alternative URI: $uri")
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                    if (uri.startsWith("waze://")) {
                        intent.setPackage("com.waze")
                    }
                    startActivity(intent)
                    Log.d(TAG, "Successfully launched with URI: $uri")
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Failed with URI $uri: ${e.message}")
                    continue
                }
            }

            false
        } catch (e: Exception) {
            Log.w(TAG, "All alternative launch methods failed: ${e.message}")
            false
        }
    }

    private fun openWazeInPlayStore() {
        try {
            // Try to open in Play Store app first
            val playStoreIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.waze"))
            startActivity(playStoreIntent)
            Log.d(TAG, "Play Store app opened for Waze")
        } catch (e: Exception) {
            try {
                // Fallback to Play Store web
                val webIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.waze"))
                startActivity(webIntent)
                Log.d(TAG, "Play Store web opened for Waze")
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to open Play Store: ${e2.message}")
                Toast.makeText(this, "Please install Waze from Play Store", Toast.LENGTH_LONG).show()
            }
        }
    }
}
