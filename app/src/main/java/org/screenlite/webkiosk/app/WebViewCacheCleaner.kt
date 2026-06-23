package org.screenlite.webkiosk.app

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object WebViewCacheCleaner {
    private const val TAG = "WebViewCacheCleaner"

    fun clearAll(context: Context) {
        Log.i(TAG, "Clearing WebView cache, cookies, and web storage")
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cookies", e)
        }

        try {
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear web storage", e)
        }

        try {
            WebView(context.applicationContext).apply {
                clearCache(true)
                clearHistory()
                destroy()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear WebView cache", e)
        }
    }
}
