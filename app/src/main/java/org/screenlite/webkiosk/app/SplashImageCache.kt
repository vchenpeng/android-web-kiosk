package org.screenlite.webkiosk.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object SplashImageCache {
    private const val TAG = "SplashImageCache"
    private const val CACHE_DIR = "splash"
    private const val CACHE_FILE = "welcome.png"

    fun getCachedFile(context: Context): File {
        return File(File(context.filesDir, CACHE_DIR), CACHE_FILE)
    }

    suspend fun prefetch(context: Context, remoteUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getCachedFile(context)
            cacheFile.parentFile?.mkdirs()
            val tempFile = File(cacheFile.parentFile, "$CACHE_FILE.tmp")

            val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
            }

            connection.connect()
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "Prefetch failed: HTTP ${connection.responseCode} for $remoteUrl")
                return@withContext false
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.renameTo(cacheFile)) {
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }

            Log.d(TAG, "Prefetched splash image to ${cacheFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Prefetch failed: $remoteUrl", e)
            false
        }
    }
}
