package org.screenlite.webkiosk.app

object SpeechSynthesisPolyfill {
    const val SCRIPT = """
(function() {
    if (window.__speechSynthesisPolyfilled) return;
    window.__speechSynthesisPolyfilled = true;

    var Native = window.AndroidSpeechSynthesis;
    if (!Native) return;

    var counter = 0;
    var speaking = false;
    var utteranceMap = {};

    function SpeechSynthesisUtterance(text) {
        this.text = String(text || '');
        this.lang = '';
        this.voice = null;
        this.volume = 1;
        this.rate = 1;
        this.pitch = 1;
        this.onend = null;
        this.onerror = null;
        this.onstart = null;
        this.onpause = null;
        this.onresume = null;
        this.onmark = null;
        this.onboundary = null;
        this._id = 'utt_' + (counter++);
        utteranceMap[this._id] = this;
    }
    window.SpeechSynthesisUtterance = SpeechSynthesisUtterance;

    window.__speechSynthesisNative = {
        onStart: function(id) {
            var utt = utteranceMap[id];
            if (utt && utt.onstart) utt.onstart({ type: 'start', utterance: utt });
        },
        onEnd: function(id) {
            var utt = utteranceMap[id];
            if (utt) {
                speaking = false;
                if (utt.onend) utt.onend({ type: 'end', utterance: utt });
                delete utteranceMap[id];
            }
        },
        onError: function(id, error) {
            var utt = utteranceMap[id];
            if (utt) {
                speaking = false;
                if (utt.onerror) utt.onerror({ type: 'error', error: error, utterance: utt });
                delete utteranceMap[id];
            }
        }
    };

    var voicesCache = [];
    var voicesChangedListeners = [];

    function loadVoices() {
        try {
            voicesCache = JSON.parse(Native.getVoicesJson() || '[]');
        } catch (e) {
            voicesCache = [];
        }
        for (var i = 0; i < voicesChangedListeners.length; i++) {
            try { voicesChangedListeners[i](); } catch (e) {}
        }
    }

    loadVoices();

    window.speechSynthesis = {
        speak: function(utterance) {
            if (!utterance || !utterance.text) return;
            speaking = true;
            var lang = utterance.lang || '';
            if (utterance.voice && utterance.voice.lang) lang = utterance.voice.lang;
            Native.speak(
                utterance._id,
                utterance.text,
                lang,
                utterance.rate || 1,
                utterance.pitch || 1,
                utterance.volume !== undefined ? utterance.volume : 1
            );
        },
        cancel: function() {
            Native.cancel();
            speaking = false;
            utteranceMap = {};
        },
        pause: function() {},
        resume: function() {},
        getVoices: function() { return voicesCache.slice(); },
        addEventListener: function(type, listener) {
            if (type === 'voiceschanged' && listener) voicesChangedListeners.push(listener);
        },
        removeEventListener: function(type, listener) {
            if (type !== 'voiceschanged' || !listener) return;
            voicesChangedListeners = voicesChangedListeners.filter(function(l) { return l !== listener; });
        }
    };

    Object.defineProperty(window.speechSynthesis, 'speaking', { get: function() { return speaking; } });
    Object.defineProperty(window.speechSynthesis, 'pending', { get: function() { return false; } });
    Object.defineProperty(window.speechSynthesis, 'paused', { get: function() { return false; } });
    Object.defineProperty(window.speechSynthesis, 'voices', { get: function() { return voicesCache.slice(); } });
    Object.defineProperty(window.speechSynthesis, 'onvoiceschanged', {
        get: function() { return this._onvoiceschanged || null; },
        set: function(fn) { this._onvoiceschanged = fn; }
    });
})();
"""
}
