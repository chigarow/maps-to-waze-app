package com.example.googlemapslinktowazeapp

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class MapsUrlToWazeUtil {
    companion object {
        private const val TAG = "MapsUrlToWazeUtil"
        // Google Places API Key - loaded from BuildConfig for security
        private val GOOGLE_PLACES_API_KEY = BuildConfig.GOOGLE_PLACES_API_KEY
        
        private val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        suspend fun extractCoordinatesFromUrl(url: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Extracting coordinates from URL: $url")
                
                // 1. Try direct coordinate extraction first
                extractCoordinatesWithRegex(url)?.let { coordinates ->
                    Log.d(TAG, "Direct regex extraction successful: $coordinates")
                    return@withContext coordinates
                }
                
                // 2. Resolve redirects to get final URL
                val resolvedUrl = resolveRedirectsToFinalUrl(url)
                Log.d(TAG, "Resolved URL: $resolvedUrl")
                
                // 3. Try coordinate extraction on resolved URL
                extractCoordinatesWithRegex(resolvedUrl)?.let { coordinates ->
                    Log.d(TAG, "Regex extraction on resolved URL successful: $coordinates")
                    return@withContext coordinates
                }
                
                // 4. Special handling for maps.app.goo.gl URLs
                if (url.contains("maps.app.goo.gl") || resolvedUrl.contains("maps.app.goo.gl")) {
                    Log.d(TAG, "Trying special short URL handling")
                    trySpecialShortUrlHandling(url, resolvedUrl)?.let { coordinates ->
                        Log.d(TAG, "Special short URL handling successful: $coordinates")
                        return@withContext coordinates
                    }
                }
                
                // 5. Try to extract place ID and use Google Places API
                val placeId = extractPlaceId(resolvedUrl)
                if (placeId != null) {
                    Log.d(TAG, "Found place ID: $placeId, trying Google Places API")
                    getCoordinatesFromPlacesApi(placeId)?.let { coordinates ->
                        Log.d(TAG, "Google Places API successful: $coordinates")
                        return@withContext coordinates
                    }
                }
                
                // 6. Try to extract CID and use Google Places API
                val cid = extractCid(resolvedUrl)
                if (cid != null) {
                    Log.d(TAG, "Found CID: $cid, trying Google Places API with CID")
                    getCoordinatesFromCid(cid)?.let { coordinates ->
                        Log.d(TAG, "CID-based extraction successful: $coordinates")
                        return@withContext coordinates
                    }
                }
                
                Log.w(TAG, "No coordinates found for URL: $url")
                return@withContext null
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in extractCoordinatesFromUrl()", e)
                return@withContext null
            }
        }

        // Special handling for maps.app.goo.gl URLs - prioritize redirect URL extraction
        private suspend fun trySpecialShortUrlHandling(originalUrl: String, resolvedUrl: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Special short URL handling for: $originalUrl -> $resolvedUrl")
                
                // STRATEGY 1: Try to get the redirect URL without following it completely
                // This often contains coordinates in the URL parameters
                try {
                    val redirectRequest = Request.Builder()
                        .url(originalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                        
                    // Create a client that doesn't follow redirects automatically
                    val noRedirectClient = client.newBuilder()
                        .followRedirects(false)
                        .build()
                        
                    val redirectResponse = noRedirectClient.newCall(redirectRequest).execute()
                    val locationHeader = redirectResponse.header("Location")
                    redirectResponse.close()
                    
                    Log.d(TAG, "Redirect response: ${redirectResponse.code}, Location: $locationHeader")
                    
                    if (locationHeader != null) {
                        // Extract coordinates from the redirect URL
                        extractCoordinatesWithRegex(locationHeader)?.let { coords ->
                            Log.d(TAG, "Found coordinates in Location header: $coords")
                            return@withContext coords
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to get redirect header: ${e.message}")
                }
                
                // STRATEGY 2: Try different user agents but focus on getting readable content
                val userAgents = listOf(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36", // Desktop first
                    "curl/7.68.0", // Simple user agent that often gets plain content
                    "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.0 Mobile/15E148 Safari/604.1"
                )
                
                for (userAgent in userAgents) {
                    try {
                        Log.d(TAG, "Trying user agent: $userAgent")
                        val request = Request.Builder()
                            .url(originalUrl)
                            .header("User-Agent", userAgent)
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "en-US,en;q=0.9")
                            .header("Accept-Encoding", "identity") // No compression to avoid binary data
                            .header("Cache-Control", "no-cache")
                            .build()
                        
                        val response = client.newCall(request).execute()
                        
                        // Always check the final URL first (most reliable method)
                        val finalUrl = response.request.url.toString()
                        if (finalUrl != originalUrl) {
                            Log.d(TAG, "URL redirected to: $finalUrl")
                            extractCoordinatesWithRegex(finalUrl)?.let { coords ->
                                Log.d(TAG, "Found coordinates in redirect URL: $coords")
                                response.close()
                                return@withContext coords
                            }
                        }
                        
                        if (response.isSuccessful) {
                            try {
                                val responseBody = response.body?.string() ?: ""
                                Log.d(TAG, "Response with $userAgent: ${response.code}, body length: ${responseBody.length}")
                                
                                // Check if we got readable content (not binary/compressed)
                                val preview = if (responseBody.length > 200) responseBody.substring(0, 200) else responseBody
                                val hasReadableContent = preview.contains("google") || preview.contains("maps") || 
                                                       preview.contains("coordinates") || preview.contains("<!DOCTYPE") ||
                                                       preview.contains("<html")
                                
                                Log.d(TAG, "Response preview (first 200 chars): $preview")
                                Log.d(TAG, "Has readable content: $hasReadableContent")
                                
                                if (hasReadableContent && responseBody.isNotEmpty()) {
                                    val coordinates = extractCoordinatesFromResponseBody(responseBody)
                                    if (coordinates != null) {
                                        Log.d(TAG, "Found coordinates in response body: $coordinates")
                                        response.close()
                                        return@withContext coordinates
                                    }
                                } else {
                                    Log.w(TAG, "Response body appears to be binary/compressed or unreadable with $userAgent")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to read response body with $userAgent: ${e.message}")
                            }
                        }
                        
                        response.close()
                        
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed with user agent $userAgent: ${e.message}")
                    }
                }
                
                return@withContext null
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in trySpecialShortUrlHandling()", e)
                return@withContext null
            }
        }

        // Extract coordinates from HTML response body - comprehensive patterns
        private fun extractCoordinatesFromResponseBody(responseBody: String): Pair<Double, Double>? {
            try {
                Log.d(TAG, "Analyzing response body of ${responseBody.length} chars for coordinate patterns")
                
                // DEBUG: Show first 500 chars of response for analysis
                val debugChunk = if (responseBody.length > 500) {
                    responseBody.substring(0, 500) + "..."
                } else {
                    responseBody
                }
                Log.d(TAG, "Response body sample: $debugChunk")
                
                // Enhanced patterns based on research - more comprehensive extraction
                val patterns = listOf(
                    // CRITICAL PATTERNS (from comprehensive research) - HIGHEST PRIORITY
                    """!3d([-+]?\d*\.?\d+)!4d([-+]?\d*\.?\d+)""".toRegex(), // Primary accurate Google Maps pattern
                    """8m2!3d([-+]?\d*\.?\d+)!4d([-+]?\d*\.?\d+)""".toRegex(), // Stack Overflow confirmed pattern
                    
                    // @ SYMBOL PATTERNS (very common in maps URLs)
                    """@([-+]?\d{1,3}\.\d+),([-+]?\d{1,3}\.\d+)""".toRegex(), // Primary @ pattern 
                    """/@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // @ with leading slash
                    """place.*?@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Place-specific @ pattern
                    
                    // JAVASCRIPT EMBEDDED PATTERNS (for iOS Safari full HTML responses)
                    """\["([-+]?\d*\.?\d+)","([-+]?\d*\.?\d+)"\]""".toRegex(), // JSON string array
                    """\[([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)\]""".toRegex(), // Simple coordinate array
                    """lat[":]\s*([-+]?\d*\.?\d+).*?lng[":]\s*([-+]?\d*\.?\d+)""".toRegex(), // JS object style
                    """latitude[":]\s*([-+]?\d*\.?\d+).*?longitude[":]\s*([-+]?\d*\.?\d+)""".toRegex(), // Full name JS
                    
                    // QUERY PARAMETER PATTERNS
                    """[?&]ll=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // ll parameter
                    """[?&]q=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // q parameter
                    """[?&]center=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // center parameter
                    
                    // ORIGINAL PATTERNS (keeping existing ones as fallbacks)
                    """!3d([-+]?\d+(?:\.\d+)?)!4d([-+]?\d+(?:\.\d+)?)""".toRegex(),
                    
                    // Data parameter patterns (Protocol Buffer format)
                    """data=.*?!3d([-+]?\d*\.?\d+).*?!4d([-+]?\d*\.?\d+)""".toRegex(),
                    """!8m2!3d([-+]?\d*\.?\d+)!4d([-+]?\d*\.?\d+)""".toRegex(),
                    
                    // Standard URL patterns
                    """@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(),
                    """ll=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(),
                    """center=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(),
                    """sll=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(),
                    """destination=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(),
                    
                    // JavaScript object patterns
                    """center:\s*\{\s*lat:\s*([-+]?\d*\.?\d+),\s*lng:\s*([-+]?\d*\.?\d+)\s*\}""".toRegex(),
                    """lat\s*:\s*([-+]?\d*\.?\d+).*?lng\s*:\s*([-+]?\d*\.?\d+)""".toRegex(),
                    """latitude['":\s]+([-+]?\d*\.?\d+).*?longitude['":\s]+([-+]?\d*\.?\d+)""".toRegex(),
                    
                    // JSON array patterns
                    """"center":\s*\[\s*([-+]?\d*\.?\d+),\s*([-+]?\d*\.?\d+)\s*\]""".toRegex(),
                    """center=\[([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)\]""".toRegex(),
                    """\[([-+]?\d{1,3}\.\d+),\s*([-+]?\d{1,3}\.\d+)\]""".toRegex(),
                    
                    // Meta tag patterns
                    """content="([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)"""".toRegex(),
                    """property="og:latitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex(),
                    """property="og:longitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex(),
                    
                    // Schema.org microdata patterns
                    """itemprop="latitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex(),
                    """itemprop="longitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex(),
                    
                    // Google specific data patterns
                    """window\.APP_INITIALIZATION_STATE.*?([-+]?\d{1,3}\.\d+).*?([-+]?\d{1,3}\.\d+)""".toRegex(),
                    """window\.APP_OPTIONS.*?([-+]?\d{1,3}\.\d+).*?([-+]?\d{1,3}\.\d+)""".toRegex(),
                    
                    // Alternative JavaScript patterns
                    """coords?\s*:\s*\[\s*([-+]?\d*\.?\d+),\s*([-+]?\d*\.?\d+)\s*\]""".toRegex(),
                    """position\s*:\s*\{\s*lat\s*:\s*([-+]?\d*\.?\d+),\s*lng\s*:\s*([-+]?\d*\.?\d+)""".toRegex(),
                    """latlng\s*:\s*"([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)"""".toRegex(),
                    
                    // Additional URL fragment patterns
                    """#.*?([-+]?\d{1,3}\.\d+),([-+]?\d{1,3}\.\d+)""".toRegex(),
                    """\?(.*&)?ll=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(),
                    
                    // CID-based patterns (from research about hexadecimal conversion)
                    """!1s0x[a-fA-F0-9]+:0x([a-fA-F0-9]+)""".toRegex()
                )
                
                // First try standard two-group patterns
                for (pattern in patterns) {
                    val match = pattern.find(responseBody)
                    if (match != null && match.groupValues.size >= 3) {
                        try {
                            val lat = match.groupValues[1].toDoubleOrNull()
                            val lng = match.groupValues[2].toDoubleOrNull()
                            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                                Log.d(TAG, "Found coordinates with pattern ${pattern.pattern}: $lat, $lng")
                                return Pair(lat, lng)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to parse coordinates from match: ${match.value}")
                        }
                    }
                }
                
                // Special handling for meta tags - need to find separate lat/lng elements
                try {
                    val latPattern = """property="og:latitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex()
                    val lngPattern = """property="og:longitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex()
                    val latMatch = latPattern.find(responseBody)
                    val lngMatch = lngPattern.find(responseBody)
                    
                    if (latMatch != null && lngMatch != null) {
                        val lat = latMatch.groupValues[1].toDoubleOrNull()
                        val lng = lngMatch.groupValues[1].toDoubleOrNull()
                        if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                            Log.d(TAG, "Found coordinates from meta tags: $lat, $lng")
                            return Pair(lat, lng)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing meta tags: ${e.message}")
                }
                
                // Special handling for schema.org microdata
                try {
                    val latPattern = """itemprop="latitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex()
                    val lngPattern = """itemprop="longitude"\s*content="([-+]?\d*\.?\d+)"""".toRegex()
                    val latMatch = latPattern.find(responseBody)
                    val lngMatch = lngPattern.find(responseBody)
                    
                    if (latMatch != null && lngMatch != null) {
                        val lat = latMatch.groupValues[1].toDoubleOrNull()
                        val lng = lngMatch.groupValues[1].toDoubleOrNull()
                        if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                            Log.d(TAG, "Found coordinates from schema.org microdata: $lat, $lng")
                            return Pair(lat, lng)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing schema.org microdata: ${e.message}")
                }
                
                // Special CID handling (convert hex to decimal for Google Places API)
                try {
                    val cidPattern = """!1s0x[a-fA-F0-9]+:0x([a-fA-F0-9]+)""".toRegex()
                    val cidMatch = cidPattern.find(responseBody)
                    if (cidMatch != null) {
                        val hexCid = cidMatch.groupValues[1]
                        Log.d(TAG, "Found CID hex: $hexCid, attempting conversion")
                        // This would require the Google Places API call we already have
                        // For now, just log it for debugging
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing CID: ${e.message}")
                }
                
                // Last resort: aggressive coordinate scanning based on research
                try {
                    Log.d(TAG, "Last resort: scanning for any coordinate-like patterns")
                    
                    // Multiple aggressive patterns for last resort
                    val lastResortPatterns = listOf(
                        // Very specific coordinate-like patterns (research-based)
                        """([-+]?\d{1,3}\.\d{6,8})[,\s]+([-+]?\d{1,3}\.\d{6,8})""".toRegex(), // High precision coords
                        """([-+]?\d{1,3}\.\d{4,7})[,\s]+([-+]?\d{1,3}\.\d{4,7})""".toRegex(), // Medium precision
                        """([-+]?\d{1,3}\.\d{1,3})[,\s]+([-+]?\d{1,3}\.\d{1,3})""".toRegex(), // Lower precision
                        
                        // Find coordinates in any comma-separated format
                        """[^\d]([-+]?\d{1,3}\.\d+),\s*([-+]?\d{1,3}\.\d+)[^\d]""".toRegex(), // Bounded by non-digits
                        """=([-+]?\d{1,3}\.\d+),\s*([-+]?\d{1,3}\.\d+)""".toRegex(), // After equals
                        """:"([-+]?\d{1,3}\.\d+),\s*([-+]?\d{1,3}\.\d+)"""".toRegex(), // In quotes
                        
                        // Look for patterns common in JavaScript embeds
                        """[\[\(]([-+]?\d{1,3}\.\d+)[,\s]+([-+]?\d{1,3}\.\d+)[\]\)]""".toRegex(), // In brackets/parentheses
                        """[=:]\s*"([-+]?\d{1,3}\.\d+),([-+]?\d{1,3}\.\d+)"""".toRegex(), // String format
                        """[=:]\s*\[([-+]?\d{1,3}\.\d+),([-+]?\d{1,3}\.\d+)\]""".toRegex(), // Array format
                    )
                    
                    // Try each last resort pattern
                    for (pattern in lastResortPatterns) {
                        val matches = pattern.findAll(responseBody)
                        Log.d(TAG, "Trying last resort pattern: ${pattern.pattern}")
                        
                        for (match in matches) {
                            val lat = match.groupValues[1].toDoubleOrNull()
                            val lng = match.groupValues[2].toDoubleOrNull()
                            if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                                Log.d(TAG, "Found coordinates with last resort pattern: $lat, $lng")
                                return Pair(lat, lng)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error in generic coordinate scanning: ${e.message}")
                }
                
                Log.d(TAG, "No coordinate patterns matched in response body")
                
            } catch (e: Exception) {
                Log.w(TAG, "Error extracting coordinates from response body: ${e.message}")
            }
            return null
        }

        private suspend fun resolveRedirectsToFinalUrl(url: String): String = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Android 11; Mobile; rv:98.0) Gecko/98.0 Firefox/98.0")
                    .build()
                    
                val response = client.newCall(request).execute()
                val finalUrl = response.request.url.toString()
                response.close()
                return@withContext finalUrl
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving redirects: ${e.message}")
                return@withContext url
            }
        }

        private fun extractCoordinatesWithRegex(url: String): Pair<Double, Double>? {
            Log.d(TAG, "Extracting coordinates from URL: $url")
            
            // Enhanced regex patterns prioritizing most common redirect URL formats
            val patterns = listOf(
                // HIGH PRIORITY: Google Maps standard formats
                """@([-+]?\d{1,3}\.\d+),([-+]?\d{1,3}\.\d+)""".toRegex(), // @lat,lng (most common)
                """/([-+]?\d{1,3}\.\d+),([-+]?\d{1,3}\.\d+)/""".toRegex(), // /lat,lng/ in path
                
                // Protocol Buffer format (research-based high priority)
                """!3d([-+]?\d*\.?\d+)!4d([-+]?\d*\.?\d+)""".toRegex(), // !3d!4d format
                """8m2!3d([-+]?\d*\.?\d+)!4d([-+]?\d*\.?\d+)""".toRegex(), // 8m2!3d!4d format
                
                // Query parameters
                """[?&]ll=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // ll parameter
                """[?&]q=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // q parameter  
                """[?&]center=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // center parameter
                """[?&]sll=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // sll parameter
                """[?&]destination=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // destination
                
                // Alternative @ formats
                """@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Standard @ format
                """place.*?@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Place @ format
                """search.*?@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Search @ format
                
                // Maps embed and data formats  
                """data=.*?!3d([-+]?\d*\.?\d+).*?!4d([-+]?\d*\.?\d+)""".toRegex(), // data parameter
                """pb=.*?3d([-+]?\d*\.?\d+).*?4d([-+]?\d*\.?\d+)""".toRegex(), // pb parameter
                """!2d([-+]?\d*\.?\d+)!3d([-+]?\d*\.?\d+)""".toRegex(), // Alternative PB format
                """!1d([-+]?\d*\.?\d+)!2d([-+]?\d*\.?\d+)""".toRegex(), // Another PB variant
                
                // Additional URL path formats
                """/maps/dir/([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Directions source
                """/maps/dir/.*?/([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Directions destination
                """/place/.*?@([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Place URL format
                
                // Encoded formats (sometimes in redirects)
                """%40([-+]?\d*\.?\d+)%2C([-+]?\d*\.?\d+)""".toRegex(), // URL encoded @lat,lng
                """=([-+]?\d*\.?\d+)%2C([-+]?\d*\.?\d+)""".toRegex(), // URL encoded coordinates
                
                // Fallback patterns (broader matching)
                """coords=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Any coords parameter
                """location=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Any location parameter
                """latlng=([-+]?\d*\.?\d+),([-+]?\d*\.?\d+)""".toRegex(), // Any latlng parameter
            )

            for (pattern in patterns) {
                val match = pattern.find(url)
                if (match != null) {
                    try {
                        val lat: Double
                        val lng: Double
                        
                        if (pattern.pattern.contains("3d") && pattern.pattern.contains("4d")) {
                            // For !3d!4d format, first group is lat, second is lng
                            lat = match.groupValues[1].toDouble()
                            lng = match.groupValues[2].toDouble()
                        } else {
                            // For standard formats, first group is lat, second is lng
                            lat = match.groupValues[1].toDouble()
                            lng = match.groupValues[2].toDouble()
                        }
                        
                        if (isValidCoordinate(lat, lng)) {
                            Log.d(TAG, "Found coordinates with pattern ${pattern.pattern}: $lat, $lng")
                            return Pair(lat, lng)
                        }
                    } catch (e: NumberFormatException) {
                        Log.w(TAG, "Invalid coordinates in match: ${match.value}")
                    }
                }
            }
            return null
        }

        private fun extractPlaceId(url: String): String? {
            val placeIdPattern = """place_id:([A-Za-z0-9_-]+)|place/[^/]+/data=.*?([A-Za-z0-9_-]+)""".toRegex()
            return placeIdPattern.find(url)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
                ?: placeIdPattern.find(url)?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }
        }

        private fun extractCid(url: String): String? {
            // Extract CID from URL
            val cidPattern = """[?&]cid=(\d+)""".toRegex()
            return cidPattern.find(url)?.groupValues?.get(1)
        }

        private suspend fun getCoordinatesFromPlacesApi(placeId: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
            try {
                val apiUrl = "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&fields=geometry&key=$GOOGLE_PLACES_API_KEY"
                
                val request = Request.Builder()
                    .url(apiUrl)
                    .build()
                    
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""
                response.close()
                
                // Parse JSON response for coordinates
                val latPattern = """"lat"\s*:\s*([-+]?\d*\.?\d+)""".toRegex()
                val lngPattern = """"lng"\s*:\s*([-+]?\d*\.?\d+)""".toRegex()
                
                val latMatch = latPattern.find(responseBody)
                val lngMatch = lngPattern.find(responseBody)
                
                if (latMatch != null && lngMatch != null) {
                    val lat = latMatch.groupValues[1].toDoubleOrNull()
                    val lng = lngMatch.groupValues[1].toDoubleOrNull()
                    
                    if (lat != null && lng != null && isValidCoordinate(lat, lng)) {
                        return@withContext Pair(lat, lng)
                    }
                }
                
            } catch (e: Exception) {
                Log.w(TAG, "Error getting coordinates from Places API: ${e.message}")
            }
            return@withContext null
        }

        private suspend fun getCoordinatesFromCid(cid: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
            try {
                // Use the CID to query Google Maps directly
                val searchUrl = "https://www.google.com/maps/search/?api=1&query_place_id=$cid"
                
                val request = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Android 11; Mobile; rv:98.0) Gecko/98.0 Firefox/98.0")
                    .build()
                    
                val response = client.newCall(request).execute()
                val finalUrl = response.request.url.toString()
                response.close()
                
                return@withContext extractCoordinatesWithRegex(finalUrl)
                
            } catch (e: Exception) {
                Log.w(TAG, "Error getting coordinates from CID: ${e.message}")
            }
            return@withContext null
        }

        private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
            return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
        }
    }
}
