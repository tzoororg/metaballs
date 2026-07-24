package org.tzoororg.metaballs

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Placeholder settings screen reachable from Settings -> Screen saver -> gear icon.
 * No options yet (single animation); a future picker for more animations lands here.
 */
class DreamSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Metaballs screensaver — no settings yet."
            setPadding(48, 48, 48, 48)
        })
    }
}
