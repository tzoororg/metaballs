package org.tzoororg.metaballs

import android.service.dreams.DreamService
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * Full-screen WebView screensaver hosting the bundled index.html (the same
 * metaballs animation deployed to GitHub Pages) via WebViewAssetLoader, which
 * serves it over a real https origin so WebGL behaves as it does on the web.
 */
class MetaballsDream : DreamService() {

    private var webView: WebView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        isInteractive = false
        isFullscreen = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        val wv = WebView(this).apply {
            setWebViewClient(object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ) = assetLoader.shouldInterceptRequest(request.url)
            })
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webView = wv
        setContentView(wv)
        wv.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onDreamingStopped() {
        // Stop the GL context / rAF loop so it doesn't keep running (and draining
        // battery) once the dream is no longer visible.
        webView?.loadUrl("about:blank")
        webView?.onPause()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        webView?.destroy()
        webView = null
        super.onDetachedFromWindow()
    }
}
