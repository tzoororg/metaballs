package org.tzoororg.metaballs

import android.service.dreams.DreamService
import android.view.WindowManager
import android.webkit.WebView

/**
 * Full-screen WebView screensaver hosting the bundled index.html (the same
 * metaballs animation deployed to GitHub Pages). See [metaballsWebView].
 */
class MetaballsDream : DreamService() {

    private var webView: WebView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = false
        isFullscreen = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val wv = metaballsWebView(this)
        webView = wv
        setContentView(wv)
        wv.loadUrl(METABALLS_URL)
    }

    override fun onDreamingStopped() {
        teardown()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        teardown()
        super.onDetachedFromWindow()
    }

    /** Idempotent: whichever callback fires first kills the loop and the WebView. */
    private fun teardown() {
        val wv = webView ?: return
        webView = null
        wv.teardownMetaballs()
    }
}
