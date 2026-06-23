package org.screenlite.webkiosk.app

import android.os.SystemClock
import androidx.compose.runtime.mutableStateOf

class SplashController {
    private val _visible = mutableStateOf(true)
    val visible = _visible
    val shownAtMs: Long = SystemClock.elapsedRealtime()

    fun dismiss() {
        if (!_visible.value) return
        _visible.value = false
    }
}
