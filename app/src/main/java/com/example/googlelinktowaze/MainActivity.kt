
package com.example.googlelinktowaze

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private var resolvingShortLink = false
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val webView = findViewById<WebView>(R.id.webView)
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.setSupportMultipleWindows(true)
    try { settings.saveFormData = true } catch (_: Exception) {}

    // Hidden WebView for resolving short links
    val hiddenWebView = WebView(this)
    hiddenWebView.settings.javaScriptEnabled = true
    hiddenWebView.settings.domStorageEnabled = true
    hiddenWebView.settings.userAgentString = webView.settings.userAgentString
    hiddenWebView.layoutParams = android.widget.LinearLayout.LayoutParams(1, 1)
    hiddenWebView.visibility = android.view.View.GONE
    (findViewById<android.view.ViewGroup>(android.R.id.content)).addView(hiddenWebView)
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        settings.userAgentString = settings.userAgentString + " Chrome/99.0.4844.94 Mobile"

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && url.startsWith("waze://")) {
                    launchWazeIntent(url)
                    return true
                }
                return false
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url != null && url.startsWith("waze://")) {
                    launchWazeIntent(url)
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (url != null && url.startsWith("waze://")) {
                    launchWazeIntent(url)
                    view?.stopLoading()
                }
            }
            override fun onReceivedHttpError(view: WebView?, request: android.webkit.WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                android.util.Log.e("WebView", "HTTP error: ${errorResponse?.statusCode} ${errorResponse?.reasonPhrase}")
            }
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                android.util.Log.e("WebView", "Error: $errorCode $description at $failingUrl")
            }
            override fun onFormResubmission(view: WebView?, dontResend: android.os.Message?, resend: android.os.Message?) {
                resend?.sendToTarget()
            }
            private fun launchWazeIntent(url: String) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    if (intent.resolveActivity(this@MainActivity.packageManager) != null) {
                        startActivity(intent)
                    } else {
                        android.widget.Toast.makeText(this@MainActivity, "Waze app is not installed!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this@MainActivity, "Waze app is not installed!", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        webView.webChromeClient = WebChromeClient()

        // Set up WebViewClient to handle waze:// URLs and web Waze redirects
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                android.util.Log.d("WAZE_DEBUG", "shouldOverrideUrlLoading: $url")
                if (url != null) {
                    // Handle direct waze:// URLs
                    if (url.startsWith("waze://")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            if (intent.resolveActivity(this@MainActivity.packageManager) != null) {
                                startActivity(intent)
                            } else {
                                android.widget.Toast.makeText(this@MainActivity, "Waze app is not installed!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(this@MainActivity, "Failed to open Waze app!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        return true
                    }
                    // Handle Waze web URLs that should open the app
                    if (url.contains("waze.com") && url.contains("navigate=yes")) {
                        android.util.Log.d("WAZE_DEBUG", "Attempting to open Waze app with URL: $url")
                        return launchWazeFromWebUrl(url)
                    }
                }
                return false
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                android.util.Log.d("WAZE_DEBUG", "shouldOverrideUrlLoading (WebResourceRequest): $url")
                return shouldOverrideUrlLoading(view, url)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // If shared from Google Maps, auto-fill and trigger the webapp
                if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
                    val sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (sharedUrl != null) {
                        view?.evaluateJavascript(
                            """
                            (function() {
                                var input = document.getElementById('url');
                                if (input) {
                                    input.value = ${toJsString(sharedUrl)};
                                    var btns = document.getElementsByTagName('button');
                                    for (var i=0; i<btns.length; i++) {
                                        if (btns[i].textContent && btns[i].textContent.toLowerCase().includes('open in waze')) {
                                            btns[i].click();
                                            return;
                                        }
                                    }
                                }
                            })();
                            """.trimIndent(),
                            null
                        )
                    }
                }
            }
        }
        // Always load the webapp
        webView.loadUrl("https://waze.papko.org/")
    }
    
    // Helper function to properly launch Waze using the Google recommended approach
    private fun launchWazeFromWebUrl(url: String): Boolean {
        android.util.Log.d("WAZE_DEBUG", "launchWazeFromWebUrl: $url")
        
        // Extract coordinates from ul.waze.com URL
        if (url.startsWith("https://ul.waze.com/ul?")) {
            val uri = android.net.Uri.parse(url)
            val ll = uri.getQueryParameter("ll")
            val navigate = uri.getQueryParameter("navigate")
            
            if (ll != null && navigate == "yes") {
                val coords = ll.split(",")
                if (coords.size == 2) {
                    val lat = coords[0].trim()
                    val lon = coords[1].trim()
                    
                    android.util.Log.d("WAZE_DEBUG", "Extracted coordinates: $lat, $lon")
                    
                    // Use the Google recommended approach
                    try {
                        // First try the official waze.com URL as per Google documentation
                        val wazeUrl = "https://waze.com/ul?ll=$lat,$lon&navigate=yes"
                        android.util.Log.d("WAZE_DEBUG", "Trying official Waze URL: $wazeUrl")
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(wazeUrl))
                        startActivity(intent)
                        return true
                    } catch (e: Exception) {
                        android.util.Log.e("WAZE_DEBUG", "Failed with official URL, trying waze:// scheme: $e")
                        
                        // Fallback to waze:// scheme
                        try {
                            val wazeSchemeUrl = "waze://?ll=$lat,$lon&navigate=yes"
                            android.util.Log.d("WAZE_DEBUG", "Trying waze:// scheme: $wazeSchemeUrl")
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(wazeSchemeUrl))
                            startActivity(intent)
                            return true
                        } catch (e2: Exception) {
                            android.util.Log.e("WAZE_DEBUG", "waze:// scheme also failed: $e2")
                            
                            // Final fallback - open Play Store
                            try {
                                android.util.Log.d("WAZE_DEBUG", "Opening Play Store for Waze")
                                val playStoreIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.waze"))
                                startActivity(playStoreIntent)
                                return true
                            } catch (e3: Exception) {
                                android.util.Log.e("WAZE_DEBUG", "Play Store also failed: $e3")
                                return false
                            }
                        }
                    }
                }
            }
        }
        return false
    }
}

// Helper to safely escape a string for JS injection
fun toJsString(str: String): String {
    return '"' + str.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'") + '"'
}
