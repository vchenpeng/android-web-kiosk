package org.screenlite.webkiosk.app

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    @Volatile
    private var initStatus: Int? = null
    private val initAttemptHistory = mutableListOf<String>()

    init {
        initializeTtsEngine()
    }

    @JavascriptInterface
    fun speak(id: String, text: String, lang: String, rate: Float, pitch: Float, volume: Float) {
        val engine = tts
        if (engine == null) {
            notifyJs("onError", id, "engine-unavailable")
            return
        }
        val webView = webViewProvider()
        if (webView == null) {
            Log.e(TAG, "WebView unavailable while speaking. id=$id")
            return
        }

        webView.post {
            if (!ready.get()) {
                val status = initStatus ?: -1
                val statusLabel = initStatusLabel(status)
                if (status == TextToSpeech.ERROR) {
                    notifyJs(
                        "onError",
                        id,
                        "engine-init-failed:status=$status($statusLabel),engine=${currentEngineName()}"
                    )
                } else {
                    notifyJs(
                        "onError",
                        id,
                        "engine-not-ready:status=$status($statusLabel),engine=${currentEngineName()}"
                    )
                }
                return@post
            }

            if (text.isBlank()) {
                notifyJs("onError", id, "empty-text")
                return@post
            }

            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager != null && audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0) {
                notifyJs("onError", id, "audio-muted")
                return@post
            }

            if (lang.isNotBlank()) {
                val locale = Locale.forLanguageTag(lang.replace('_', '-'))
                when (engine.setLanguage(locale)) {
                    TextToSpeech.LANG_MISSING_DATA,
                    TextToSpeech.LANG_NOT_SUPPORTED -> {
                        Log.w(TAG, "Language not available offline: $lang")
                        notifyJs("onError", id, "language-unavailable:$lang")
                        return@post
                    }
                }
            }

            engine.setSpeechRate(rate.coerceIn(0.1f, 10f))
            engine.setPitch(pitch.coerceIn(0.1f, 2f))

            val params = Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume.coerceIn(0f, 1f))
            }

            try {
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
                if (result == TextToSpeech.ERROR) {
                    notifyJs("onError", id, "speak-returned-error:engine=${currentEngineName()}")
                } else {
                    Log.d(
                        TAG,
                        "Queued TTS id=$id lang=$lang engine=${currentEngineName()} volume=${volume.coerceIn(0f, 1f)}"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Native speak exception id=$id", e)
                notifyJs("onError", id, "native-exception:${e.javaClass.simpleName}")
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

    @JavascriptInterface
    fun getTtsDebugInfo(): String {
        val engine = tts
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val streamVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: -1
        val streamMaxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: -1
        val isMusicActive = audioManager?.isMusicActive ?: false
        val status = initStatus ?: -1
        val statusLabel = initStatusLabel(status)
        val configuredDefaultEngine = Settings.Secure.getString(
            appContext.contentResolver,
            "tts_default_synth"
        ) ?: "unknown"

        val availableEngines = JSONArray().apply {
            (engine?.engines ?: emptyList()).forEach { item ->
                put(
                    JSONObject()
                        .put("name", item.name)
                        .put("label", item.label?.toString() ?: "")
                )
            }
        }
        val initAttempts = JSONArray().apply {
            initAttemptHistory.forEach { put(it) }
        }

        return JSONObject()
            .put("ready", ready.get())
            .put("initStatus", status)
            .put("initStatusLabel", statusLabel)
            .put("engineName", engine?.defaultEngine ?: "unknown-engine")
            .put("configuredDefaultEngine", configuredDefaultEngine)
            .put("availableEngines", availableEngines)
            .put("initAttempts", initAttempts)
            .put("defaultLocale", Locale.getDefault().toLanguageTag())
            .put("streamMusicVolume", streamVolume)
            .put("streamMusicMaxVolume", streamMaxVolume)
            .put("isMusicActive", isMusicActive)
            .toString()
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

    private fun initializeTtsEngine() {
        ready.set(false)
        initStatus = null
        initAttemptHistory.clear()

        val configuredDefaultEngine = Settings.Secure.getString(
            appContext.contentResolver,
            "tts_default_synth"
        )

        val candidates = listOf(
            configuredDefaultEngine?.takeIf { it.isNotBlank() },
            GOOGLE_TTS_ENGINE,
            null
        ).distinct()

        tryInitCandidate(candidates, 0)
    }

    private fun tryInitCandidate(candidates: List<String?>, index: Int) {
        if (index >= candidates.size) {
            initStatus = TextToSpeech.ERROR
            ready.set(false)
            tts = null
            Log.e(TAG, "All TTS init candidates failed: $initAttemptHistory")
            return
        }

        val requestedEngine = candidates[index]
        val requestedLabel = requestedEngine ?: "system-default"
        initAttemptHistory.add(requestedLabel)

        var localTts: TextToSpeech? = null
        val instance = if (requestedEngine != null) {
            TextToSpeech(appContext, { status ->
                onInitResult(status, localTts, requestedEngine, candidates, index)
            }, requestedEngine)
        } else {
            TextToSpeech(appContext, { status ->
                onInitResult(status, localTts, null, candidates, index)
            })
        }
        localTts = instance
    }

    private fun onInitResult(
        status: Int,
        instance: TextToSpeech?,
        requestedEngine: String?,
        candidates: List<String?>,
        index: Int
    ) {
        initStatus = status
        if (status == TextToSpeech.SUCCESS && instance != null) {
            tts?.shutdown()
            tts = instance
            ready.set(true)
            attachProgressListener(instance)
            Log.d(TAG, "TextToSpeech initialized. requested=${requestedEngine ?: "system-default"}")
            return
        }

        instance?.shutdown()
        ready.set(false)
        Log.e(TAG, "TTS init failed. requested=${requestedEngine ?: "system-default"}, status=$status")
        tryInitCandidate(candidates, index + 1)
    }

    private fun attachProgressListener(engine: TextToSpeech) {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                notifyJs("onStart", utteranceId, null)
            }

            override fun onDone(utteranceId: String?) {
                notifyJs("onEnd", utteranceId, null)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                notifyJs("onError", utteranceId, "synthesis-failed:legacy")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                notifyJs("onError", utteranceId, "synthesis-failed:$errorCode")
            }
        })
    }

    private fun currentEngineName(): String {
        return tts?.defaultEngine ?: "unknown-engine"
    }

    private fun initStatusLabel(status: Int): String {
        return when (status) {
            TextToSpeech.SUCCESS -> "SUCCESS"
            TextToSpeech.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
    }

    companion object {
        private const val TAG = "SpeechSynthesisBridge"
        private const val GOOGLE_TTS_ENGINE = "com.google.android.tts"
    }
}
