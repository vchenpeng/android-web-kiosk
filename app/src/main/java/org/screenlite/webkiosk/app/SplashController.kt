package org.screenlite.webkiosk.app

import androidx.compose.runtime.mutableStateOf

class SplashController {
    private val _visible = mutableStateOf(true)
    val visible = _visible

    @Volatile
    var keepOnScreen: Boolean = true
        private set

    fun dismiss() {
        if (!_visible.value) return
        keepOnScreen = false
        _visible.value = false
    }
}
