package org.screenlite.webkiosk.app

import android.net.Uri

object SplashImageUrls {
    private const val REMOTE_PATH = "/robot/welcome.png"

    fun remoteWelcomeUrl(startUrl: String): String? {
        val trimmed = startUrl.trim()
        if (trimmed.isBlank()) return null

        return try {
            val uri = Uri.parse(trimmed)
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            val authority = if (uri.port != -1) "$host:${uri.port}" else host
            "$scheme://$authority$REMOTE_PATH"
        } catch (_: Exception) {
            null
        }
    }
}
