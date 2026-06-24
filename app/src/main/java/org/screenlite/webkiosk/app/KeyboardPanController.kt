package org.screenlite.webkiosk.app

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class KeyboardPanController {
    private val tag = "KeyboardPanController"

    var panPx by mutableIntStateOf(0)
        private set

    var webView: WebView? = null

    private var lastInputBottomCss = 0.0
    private var lastViewportHeightCss = 0.0

    fun onInputFocus(inputBottomCss: Double, viewportHeightCss: Double, context: Context) {
        lastInputBottomCss = inputBottomCss
        lastViewportHeightCss = viewportHeightCss
        recalculate(context)
    }

    fun recalculate(context: Context) {
        if (lastViewportHeightCss <= 0.0) return

        val webView = webView ?: return
        val webViewHeight = webView.height
        if (webViewHeight <= 0) return

        val imeBottom = ViewCompat.getRootWindowInsets(webView)
            ?.getInsets(WindowInsetsCompat.Type.ime())
            ?.bottom ?: 0

        if (imeBottom == 0) {
            clear()
            return
        }

        val scale = webViewHeight / lastViewportHeightCss.toFloat()
        val inputBottomPx = (lastInputBottomCss * scale).toFloat()
        val availablePx = webViewHeight - imeBottom
        val paddingPx = 16f * context.resources.displayMetrics.density
        val overflow = (inputBottomPx - availablePx + paddingPx).coerceAtLeast(0f)
        panPx = overflow.toInt()

        Log.d(
            tag,
            "Pan inputBottom=${inputBottomPx.toInt()} available=$availablePx ime=$imeBottom pan=$panPx"
        )
    }

    fun clear() {
        lastInputBottomCss = 0.0
        lastViewportHeightCss = 0.0
        if (panPx != 0) {
            Log.d(tag, "Clear keyboard pan")
        }
        panPx = 0
    }
}
