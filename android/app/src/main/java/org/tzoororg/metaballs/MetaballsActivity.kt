package org.tzoororg.metaballs

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.WebView

/**
 * Launcher icon -> fullscreen metaballs. Same WebView as the Dream; exists
 * because MIUI blocks third-party DreamServices from actually starting.
 * Back button exits; that's the only control.
 */
class MetaballsActivity : Activity() {

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // ponytail: deprecated systemUiVisibility instead of WindowInsetsController —
        // one line, works on minSdk 24 without a compat branch.
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        val wv = metaballsWebView(this)
        webView = wv
        setContentView(wv)
        wv.loadUrl(METABALLS_URL)
    }

    override fun onDestroy() {
        webView?.teardownMetaballs()
        webView = null
        super.onDestroy()
    }
}
