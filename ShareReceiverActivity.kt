package com.reelsaver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.reelsaver.service.DownloadService

/**
 * SILENT share receiver — Theme.NoDisplay means ZERO UI shown.
 * Instagram never loses focus. User stays on the reel.
 */
class ShareReceiverActivity : Activity() {

    companion object {
        private const val TAG = "ReelSaver"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            if (intent?.action == Intent.ACTION_SEND &&
                intent.type == "text/plain") {

                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                Log.d(TAG, "Received share: $sharedText")

                val url = extractInstagramUrl(sharedText)

                if (url != null) {
                    Log.d(TAG, "Extracted URL: $url")
                    val serviceIntent = Intent(this, DownloadService::class.java).apply {
                        putExtra(DownloadService.EXTRA_URL, url)
                    }
                    startForegroundService(serviceIntent)
                } else {
                    Log.w(TAG, "No Instagram URL found in: $sharedText")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in ShareReceiver", e)
        }

        // CRITICAL: finish() immediately so user stays on Instagram
        finish()
    }

    private fun extractInstagramUrl(text: String?): String? {
        if (text == null) return null
        // Match instagram reel, post, or story URLs
        val patterns = listOf(
            Regex("""https?://(?:www\.)?instagram\.com/reel/[\w\-]+/?(?:\?[^\s]*)?"""),
            Regex("""https?://(?:www\.)?instagram\.com/p/[\w\-]+/?(?:\?[^\s]*)?"""),
            Regex("""https?://(?:www\.)?instagram\.com/stories/[\w\-]+/\d+/?(?:\?[^\s]*)?"""),
            Regex("""https?://(?:www\.)?instagram\.com/tv/[\w\-]+/?(?:\?[^\s]*)?""")
        )
        for (pattern in patterns) {
            val match = pattern.find(text)
            if (match != null) return match.value
        }
        return null
    }
}
