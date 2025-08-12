
package com.example.googlelinktowaze

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern

object GoogleMapsToWazeUtil {
    private val coordRegex = Regex("([-+]?\\d+\\.\\d+),\\s*([-+]?\\d+\\.\\d+)")
    private val degreeCoordRegex = Regex("(\\d+)%C2%B0(\\d+)'(\\d+\\.\\d+)%22([NS])\\+(\\d+)%C2%B0(\\d+)'(\\d+\\.\\d+)%22([EW])")
    private val client = OkHttpClient()

    // Optionally set your Google Places API key here if you want to support CID fallback
    private const val GOOGLE_API_KEY: String = "" // TODO: Set this if you want to support CID fallback

    fun isValidGoogleUrl(url: String?): Boolean {
        if (url.isNullOrBlank() || url.length > 512) return false
        val lower = url.lowercase()
        return lower.contains("google.com") || lower.contains("goo.gl") || lower.contains("maps.app.goo.gl")
    }

    // Main suspend function to resolve and extract coordinates, matching Python fallback logic
    suspend fun resolveAndExtractCoordinates(url: String): Pair<Double, Double>? {
        return try {
            val resolvedUrl = resolveFinalUrlSuspend(url) ?: url
            // 1. Fasttrack: try direct coordinate extraction (with degree symbol)
            parseDirectCoordinates(resolvedUrl)?.let { return it }
            // 2. If /place/ or /dir/ in URL, skip unless last resort
            if (resolvedUrl.contains("/place/") || resolvedUrl.contains("/dir/")) {
                // skip, unless last resort
                // (Python skips these unless last_resort)
                // We'll try last resort below
            } else {
                // 3. Try normal coordinate regex
                extractCoordinatesWithRegex(resolvedUrl)?.let { return it }
            }
            // 4. Try CID extraction and Google Places API (if API key is set)
            val cid = placesApiParseCid(resolvedUrl)
            if (cid != null && GOOGLE_API_KEY.isNotBlank()) {
                getCoordinatesFromPlaceId(cid, GOOGLE_API_KEY)?.let { return it }
            }
            // 5. Last resort: try coordinate regex even for /place/ and /dir/
            extractCoordinatesWithRegex(resolvedUrl, lastResort = true)?.let { return it }
            null
        } catch (e: Exception) {
            null
        }
    }

    // Helper: resolve final URL after redirects
    private suspend fun resolveFinalUrlSuspend(url: String): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val request = Request.Builder().url(url).head().build()
                client.newCall(request).execute().use { response ->
                    response.request.url.toString()
                }
            } catch (e: IOException) {
                null
            }
        }

    // Helper: parse coordinates with degree symbol (encoded)
    private fun parseDirectCoordinates(encoded: String): Pair<Double, Double>? {
        val match = degreeCoordRegex.find(encoded) ?: return null
        val (latDeg, latMin, latSec, latDir, lonDeg, lonMin, lonSec, lonDir) = match.destructured
        var latitude = latDeg.toDouble() + latMin.toDouble() / 60 + latSec.toDouble() / 3600
        if (latDir == "S") latitude = -latitude
        var longitude = lonDeg.toDouble() + lonMin.toDouble() / 60 + lonSec.toDouble() / 3600
        if (lonDir == "W") longitude = -longitude
        return Pair(latitude, longitude)
    }

    // Helper: extract coordinates with regex, skip /place/ and /dir/ unless lastResort
    private fun extractCoordinatesWithRegex(url: String, lastResort: Boolean = false): Pair<Double, Double>? {
        if (!lastResort) {
            if (url.contains("%C2%B0")) {
                // Already handled by parseDirectCoordinates
                return null
            }
            if (url.contains("/place/") || url.contains("/dir/")) {
                return null
            }
        }
        val match = coordRegex.find(url)
        return match?.let {
            val (lat, lon) = it.destructured
            lat.trimStart('+').toDoubleOrNull()?.let { latitude ->
                lon.trimStart('+').toDoubleOrNull()?.let { longitude ->
                    Pair(latitude, longitude)
                }
            }
        }
    }

    // Helper: parse CID from URL (hex to decimal)
    private fun placesApiParseCid(url: String): String? {
        val patterns = listOf(
            "ftid.*:(\\w+)",
            "/data=.*0x(\\w+)"
        )
        for (pattern in patterns) {
            val regex = Pattern.compile(pattern)
            val matcher = regex.matcher(url)
            if (matcher.find()) {
                val cidHex = matcher.group(1)
                return try {
                    cidHex?.let { it.toLong(16).toString() }
                } catch (e: Exception) {
                    null
                }
            }
        }
        return null
    }

    // Helper: Google Places API call (if API key is set)
    private fun getCoordinatesFromPlaceId(cid: String, apiKey: String): Pair<Double, Double>? {
        try {
            val url = "https://maps.googleapis.com/maps/api/place/details/json?cid=$cid&key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("status") == "OK") {
                    val result = json.optJSONObject("result") ?: return null
                    val geometry = result.optJSONObject("geometry") ?: return null
                    val location = geometry.optJSONObject("location") ?: return null
                    val lat = location.optDouble("lat")
                    val lng = location.optDouble("lng")
                    return Pair(lat, lng)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return null
    }

    fun buildWazeUri(lat: Double, lon: Double): String {
        return "waze://?ll=$lat,$lon&navigate=yes"
    }
}
