package com.reelsaver.util

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class DownloadResult(
    val success: Boolean,
    val fileName: String? = null,
    val error: String? = null
)

class YtDlpRunner(private val context: Context) {

    companion object {
        private const val TAG = "YtDlpRunner"
        private const val BINARY_NAME = "yt-dlp"
    }

    /**
     * Returns path to the yt-dlp binary.
     * On first run, copies it from assets to internal storage and makes it executable.
     */
    private fun getBinaryPath(): String {
        val binFile = File(context.filesDir, BINARY_NAME)
        if (!binFile.exists() || !binFile.canExecute()) {
            // Copy from assets
            context.assets.open(BINARY_NAME).use { input ->
                binFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            binFile.setExecutable(true, true)
            Log.d(TAG, "yt-dlp binary installed at ${binFile.absolutePath}")
        }
        return binFile.absolutePath
    }

    suspend fun download(
        url: String,
        outputDir: String,
        quality: String = "1080",
        onProgress: (Int) -> Unit = {}
    ): DownloadResult {
        return try {
            val binaryPath = getBinaryPath()

            // Format string: best video up to chosen quality + best audio, merged to mp4
            val formatStr = when (quality) {
                "720"  -> "bestvideo[height<=720]+bestaudio/best[height<=720]"
                "480"  -> "bestvideo[height<=480]+bestaudio/best[height<=480]"
                else   -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
            }

            val outputTemplate = "$outputDir/%(uploader)s_%(id)s.%(ext)s"

            val cmd = arrayOf(
                binaryPath,
                "--no-playlist",
                "--format", formatStr,
                "--merge-output-format", "mp4",
                "--output", outputTemplate,
                // Remove watermark by getting clean CDN stream
                "--add-header", "User-Agent:Mozilla/5.0",
                // Progress output parseable
                "--newline",
                "--progress",
                url
            )

            Log.d(TAG, "Running: ${cmd.joinToString(" ")}")

            val process = Runtime.getRuntime().exec(cmd)
            var lastFileName: String? = null

            // Parse stdout for progress
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))

            val errorThread = Thread {
                errorReader.lines().forEach { line ->
                    Log.d(TAG, "yt-dlp stderr: $line")
                }
            }.also { it.start() }

            reader.lines().forEach { line ->
                Log.d(TAG, "yt-dlp: $line")
                // Parse progress: "[download]  42.3% of ..."
                if (line.contains("[download]") && line.contains("%")) {
                    val percentMatch = Regex("""(\d+\.\d+)%""").find(line)
                    percentMatch?.groupValues?.get(1)?.toFloatOrNull()?.let { pct ->
                        onProgress(pct.toInt())
                    }
                }
                // Detect output file
                if (line.startsWith("[Merger]") || line.contains("Destination:")) {
                    lastFileName = line.substringAfterLast("/")
                        .substringAfterLast("\\")
                        .trim()
                }
            }

            errorThread.join(5000)
            val exitCode = process.waitFor()

            if (exitCode == 0) {
                DownloadResult(success = true, fileName = lastFileName ?: "reel.mp4")
            } else {
                DownloadResult(success = false, error = "yt-dlp exited with code $exitCode")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception during download", e)
            DownloadResult(success = false, error = e.message)
        }
    }
}
