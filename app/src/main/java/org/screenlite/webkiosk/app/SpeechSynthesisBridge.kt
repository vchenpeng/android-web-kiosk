package org.screenlite.webkiosk.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class SpeechSynthesisBridge(
    context: Context,
    private val webViewProvider: () -> WebView?
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready.set(true)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        notifyJs("onStart", utteranceId, null)
                    }

                    override fun onDone(utteranceId: String?) {
                        notifyJs("onEnd", utteranceId, null)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        notifyJs("onError", utteranceId, "synthesis-failed")
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        notifyJs("onError", utteranceId, "synthesis-failed")
                    }
                })
                Log.d(TAG, "TextToSpeech initialized")
            } else {
                Log.e(TAG, "TextToSpeech initialization failed: status=$status")
            }
        }
    }

    @JavascriptInterface
    fun speak(id: String, text: String, lang: String, rate: Float, pitch: Float, volume: Float) {
        val engine = tts ?: return
        webViewProvider()?.post {
            if (!ready.get()) {
                notifyJs("onError", id, "not-allowed")
                return@post
            }

            if (lang.isNotBlank()) {
                val locale = Locale.forLanguageTag(lang.replace('_', '-'))
                when (engine.setLanguage(locale)) {
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.w(TAG, "Language not available offline: $lang")
                        notifyJs("onError", id, "language-unavailable")
                        return@post
                    }
                }
            }

            engine.setSpeechRate(rate.coerceIn(0.1f, 10f))
            engine.setPitch(pitch.coerceIn(0.1f, 2f))

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
            }

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
            if (result == TextToSpeech.ERROR) {
                notifyJs("onError", id, "synthesis-failed")
            }
        }
    }

    @JavascriptInterface
    fun cancel() {
        webViewProvider()?.post {
            tts?.stop()
        }
    }

    @JavascriptInterface
    fun getVoicesJson(): String {
        val engine = tts ?: return "[]"
        if (!ready.get()) return "[]"

        val voices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            engine.voices ?: emptySet()
        } else {
            emptySet()
        }

        val array = JSONArray()
        for (voice in voices) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                array.put(
                    JSONObject()
                        .put("name", voice.name)
                        .put("lang", voice.locale.toLanguageTag())
                        .put("localService", !voice.isNetworkConnectionRequired)
                        .put("default", false)
                )
            }
        }

        if (array.length() == 0) {
            array.put(
                JSONObject()
                    .put("name", "default")
                    .put("lang", Locale.getDefault().toLanguageTag())
                    .put("localService", true)
                    .put("default", true)
            )
        }

        return array.toString()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready.set(false)
    }

    private fun notifyJs(method: String, utteranceId: String?, error: String?) {
        val webView = webViewProvider() ?: return
        val safeId = utteranceId?.replace("'", "\\'") ?: ""
        val script = if (error != null) {
            val safeError = error.replace("'", "\\'")
            "window.__speechSynthesisNative.$method('$safeId','$safeError')"
        } else {
            "window.__speechSynthesisNative.$method('$safeId')"
        }
        webView.post {
            webView.evaluateJavascript(script, null)
        }
    }

    companion object {
        private const val TAG = "SpeechSynthesisBridge"
    }
}
