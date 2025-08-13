package com.example.googlelinktowaze

import android.util.Patterns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.regex.Pattern

object MapsUrlToWazeUtil {
    private val coordRegex = Regex("([-+]?\\d+\\.\\d+),\\s*([-+]?\\d+\\.\\d+)")
    private val degreeCoordRegex = Regex("(\\d+)%C2%B0(\\d+)'(\\d+\\.\\d+)%22([NS])\\+(\\d+)%C2%B0(\\d+)'(\\d+\\.\\d+)%22([EW])")
    private val client = OkHttpClient()
    private const val GOOGLE_API_KEY = "xxx"

    suspend fun extractCoordinatesFromUrl(url: String): Pair<Double, Double>? {
        val resolvedUrl = resolveFinalUrl(url) ?: url
        // 1. Try direct degree symbol coordinates
        parseDirectCoordinates(resolvedUrl)?.let { return it }
        // 2. Try normal coordinate regex
        extractCoordinatesWithRegex(resolvedUrl)?.let { return it }
        // 3. Try CID/Place ID extraction and Google Places API
        val cid = placesApiParseCid(resolvedUrl)
        if (cid != null) {
            getCoordinatesFromPlaceId(cid)?.let { return it }
        }
        // 4. Last resort: try coordinate regex again
        extractCoordinatesWithRegex(resolvedUrl, lastResort = true)?.let { return it }
        return null
    }

    private suspend fun resolveFinalUrl(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Mobile Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                android.util.Log.d("WAZE_DEBUG", "Resolved short link: $url -> $finalUrl")
                finalUrl
            }
        } catch (e: Exception) {
            android.util.Log.e("WAZE_DEBUG", "Failed to resolve short link: $url", e)
            null
        }
    }

    private fun parseDirectCoordinates(encoded: String): Pair<Double, Double>? {
        val match = degreeCoordRegex.find(encoded) ?: return null
        val (latDeg, latMin, latSec, latDir, lonDeg, lonMin, lonSec, lonDir) = match.destructured
        var latitude = latDeg.toDouble() + latMin.toDouble() / 60 + latSec.toDouble() / 3600
        if (latDir == "S") latitude = -latitude
        var longitude = lonDeg.toDouble() + lonMin.toDouble() / 60 + lonSec.toDouble() / 3600
        if (lonDir == "W") longitude = -longitude
        return Pair(latitude, longitude)
    }

    fun extractCoordinatesWithRegex(url: String, lastResort: Boolean = false): Pair<Double, Double>? {
        if (!lastResort) {
            if (url.contains("%C2%B0")) return null
            if (url.contains("/place/") || url.contains("/dir/")) return null
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

    private suspend fun getCoordinatesFromPlaceId(cid: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/place/details/json?cid=$cid&key=$GOOGLE_API_KEY"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("status") == "OK") {
                    val result = json.optJSONObject("result") ?: return@withContext null
                    val geometry = result.optJSONObject("geometry") ?: return@withContext null
                    val location = geometry.optJSONObject("location") ?: return@withContext null
                    val lat = location.optDouble("lat")
                    val lng = location.optDouble("lng")
                    return@withContext Pair(lat, lng)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return@withContext null
    }

    fun buildWazeWebUri(lat: Double, lon: Double): String {
        // Match Python: https://ul.waze.com/ul?ll=LAT,LON&navigate=yes
        return "https://ul.waze.com/ul?ll=$lat,$lon&navigate=yes"
    }
}
