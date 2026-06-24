package org.screenlite.webkiosk.app

import android.webkit.WebView

object KeyboardPanInjector {
    private const val SCRIPT = """
        (function() {
            if (window.__kioskKeyboardPanHelper) return;
            window.__kioskKeyboardPanHelper = true;

            function isInput(el) {
                if (!el || !el.tagName) return false;
                var tag = el.tagName.toLowerCase();
                return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
            }

            function viewportHeight() {
                if (window.visualViewport && window.visualViewport.height > 0) {
                    return window.visualViewport.height;
                }
                return window.innerHeight || document.documentElement.clientHeight || 0;
            }

            function reportPan(target) {
                if (!target || !isInput(target)) return;
                var rect = target.getBoundingClientRect();
                try {
                    NativeBridge.reportInputFocus(rect.bottom, viewportHeight());
                } catch (e) {}
            }

            function clearPan() {
                try { NativeBridge.clearInputFocus(); } catch (e) {}
            }

            var focusedInput = null;

            document.addEventListener('focusin', function(e) {
                if (!isInput(e.target)) return;
                focusedInput = e.target;
                setTimeout(function() { reportPan(focusedInput); }, 100);
                setTimeout(function() { reportPan(focusedInput); }, 350);
            }, true);

            document.addEventListener('focusout', function(e) {
                if (!isInput(e.target)) return;
                focusedInput = null;
                clearPan();
            }, true);

            if (window.visualViewport) {
                window.visualViewport.addEventListener('resize', function() {
                    if (focusedInput) reportPan(focusedInput);
                });
            }
        })();
    """

    fun inject(webView: WebView) {
        webView.evaluateJavascript(SCRIPT, null)
    }
}
