package org.screenlite.webkiosk.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.screenlite.webkiosk.app.WebViewManager
import org.screenlite.webkiosk.data.KioskSettingsFactory
import org.screenlite.webkiosk.data.Rotation
import org.screenlite.webkiosk.service.StayOnTopService

private const val TAG = "WebViewComponent"
private const val SPLASH_TIMEOUT_MS = 10_000L
private const val MIN_SPLASH_DISPLAY_MS = 3_000L

@Composable
fun WebViewComponent(
    url: String,
    activity: Activity,
    modifier: Modifier = Modifier,
    onDismissSplash: () -> Unit,
) {
    val context = LocalContext.current
    val kioskSettings = remember { KioskSettingsFactory.get(context) }

    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var hasLoadedPage by remember { mutableStateOf(false) }
    var rotation: Rotation by remember { mutableStateOf(Rotation.ROTATION_0) }
    var retryCount by remember { mutableIntStateOf(0) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var splashDismissed by remember { mutableStateOf(false) }
    var splashDismissRequested by remember { mutableStateOf(false) }
    val splashShownAtMs = remember { SystemClock.elapsedRealtime() }

    val dismissSplash = remember(onDismissSplash) {
        {
            if (!splashDismissed) {
                Log.d(TAG, "Dismissing splash screen")
                splashDismissed = true
                onDismissSplash()
            }
        }
    }

    val requestDismissSplash = remember {
        {
            if (!splashDismissRequested) {
                splashDismissRequested = true
            }
        }
    }

    LaunchedEffect(splashDismissRequested, splashDismissed) {
        if (splashDismissRequested && !splashDismissed) {
            val elapsed = SystemClock.elapsedRealtime() - splashShownAtMs
            val remaining = (MIN_SPLASH_DISPLAY_MS - elapsed).coerceAtLeast(0L)
            if (remaining > 0) {
                Log.d(TAG, "Splash minimum duration not met, delaying dismiss by ${remaining}ms")
                delay(remaining)
            }
            dismissSplash()
        }
    }

    val webViewManager = remember {
        WebViewManager(
            activity,
            onError = { err ->
                Log.e(TAG, "WebView error: $err")
                hasError = err
                if (err) {
                    hasLoadedPage = false
                }
            },
            onPageLoading = { loading ->
                isLoading = loading
                Log.d(TAG, "Page loading=$loading")
            },
            onPageReady = {
                hasLoadedPage = true
                hasError = false
                Log.d(TAG, "Page loaded successfully")
            }
        )
    }

    LaunchedEffect(Unit) {
        delay(SPLASH_TIMEOUT_MS)
        if (!splashDismissed) {
            Log.d(TAG, "Splash timeout - auto hiding splash screen")
            requestDismissSplash()
        }
    }

    LaunchedEffect(hasLoadedPage) {
        if (hasLoadedPage && !splashDismissed) {
            requestDismissSplash()
        }
    }

    val kioskInterface = remember(dismissSplash) {
        object {
            @JavascriptInterface
            fun hideSplash() {
                (context as? Activity)?.runOnUiThread {
                    Log.d(TAG, "JS called hideSplash()")
                    requestDismissSplash()
                    isLoading = false
                    hasError = false
                    hasLoadedPage = true
                }
            }

            @JavascriptInterface
            fun exitApp() {
                (context as? Activity)?.runOnUiThread {
                    Log.d(TAG, "JS called exitApp()")
                    try {
                        context.stopService(Intent(context, StayOnTopService::class.java))
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to stop StayOnTopService before exit", e)
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        (context as? Activity)?.finishAndRemoveTask()
                    } else {
                        (context as? Activity)?.finishAffinity()
                    }
                }
            }

            @JavascriptInterface
            fun getEntryUrl(): String {
                return try {
                    runBlocking { kioskSettings.getStartUrl().first() }
                } catch (e: Exception) {
                    Log.e(TAG, "getEntryUrl() failed", e)
                    ""
                }
            }

            @JavascriptInterface
            fun setEntryUrl(url: String): Boolean {
                val normalized = url.trim()
                if (normalized.isBlank()) {
                    Log.w(TAG, "setEntryUrl() rejected: empty url")
                    return false
                }

                return try {
                    runBlocking {
                        kioskSettings.setStartUrl(normalized)
                    }
                    Log.d(TAG, "setEntryUrl() success: $normalized")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "setEntryUrl() failed: $normalized", e)
                    false
                }
            }
        }
    }

    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.orientation) {
        val orientation =
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) "LANDSCAPE"
            else "PORTRAIT"

        Log.d(TAG, "Device orientation changed: $orientation")
        webViewManager.updateRotation(rotation)
    }

    LaunchedEffect(Unit) {
        kioskSettings.getRotation().collect { newRotation ->
            Log.d(TAG, "Rotation updated: $newRotation")
            rotation = newRotation
            webViewManager.updateRotation(newRotation)
        }
    }

    LaunchedEffect(hasError, retryTrigger) {
        if (hasError && !hasLoadedPage) {
            retryCount++
            val delayTime = (1000L * (1 shl (retryCount - 1))).coerceAtMost(30_000L)
            Log.d(TAG, "Retry #$retryCount in ${delayTime}ms (trigger=$retryTrigger)")
            delay(delayTime)
            retryTrigger++
        } else if (!hasError) {
            if (retryCount > 0) Log.d(TAG, "Reset retry count (error cleared)")
            retryCount = 0
        }
    }

    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                if (hasError) {
                    hasError = false
                    retryTrigger++
                    Log.d(TAG, "Recovered from error, retryTrigger=$retryTrigger")
                }
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                if (!isLoading && !hasLoadedPage) {
                    Log.e(TAG, "Connection lost before page loaded")
                    hasError = true
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback)
        } else {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            @Suppress("DEPRECATION")
            cm.registerNetworkCallback(networkRequest, callback)
        }

        onDispose {
            Log.d(TAG, "Unregistering network callback")
            cm.unregisterNetworkCallback(callback)
            webViewManager.destroy()
        }
    }

    key(retryTrigger) {
        AndroidView(
            modifier = modifier,
            factory = {
                Log.d(TAG, "Creating WebView (rotation=$rotation)")
                val webView = webViewManager.createWebView(rotation)

                webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webView.visibility = View.INVISIBLE

                webView.settings.javaScriptEnabled = true
                webView.addJavascriptInterface(kioskInterface, "NativeBridge")

                webView
            },
            update = { webView ->
                if (webView.url != url) {
                    Log.d(TAG, "Loading new URL: $url")
                    webView.loadUrl(url)
                } else if (retryTrigger > 0 && !hasLoadedPage) {
                    Log.d(TAG, "Retry triggered, reloading WebView")
                    webView.reload()
                }
            })
    }
}
