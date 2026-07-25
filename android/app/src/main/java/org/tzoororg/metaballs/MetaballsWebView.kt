package org.tzoororg.metaballs

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader

/**
 * The animation URL. WebViewAssetLoader serves the bundled asset over a real
 * https origin so WebGL behaves as it does on the web.
 *
 * fps/scale cap the render loop: 30fps at half resolution is visually identical
 * for a blob shader and a quarter of the fragment work. Uncapped full-res cooked
 * the GPU overnight (60 C idle, thermal throttle).
 */
const val METABALLS_URL =
    "https://appassets.androidplatform.net/assets/index.html?fps=30&scale=0.5"

/** Shared by the Dream and the launcher Activity — same WebView, same asset. */
fun metaballsWebView(context: Context): WebView {
    val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

    return WebView(context).apply {
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
}

/** Idempotent teardown: kills the render loop and the WebView. */
fun WebView.teardownMetaballs() {
    loadUrl("about:blank")
    onPause()
    pauseTimers()
    (parent as? android.view.ViewGroup)?.removeView(this)
    destroy()
}
