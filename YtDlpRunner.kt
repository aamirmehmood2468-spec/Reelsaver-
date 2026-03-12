package com.reelsaver.util

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL

data class DownloadResult(
    val success: Boolean,
    val fileName: String? = null,
    val error: String? = null
)

class YtDlpRunner(private val context: Context) {

    companion object {
        private const val TAG = "YtDlpRunner"
        private const val YTDLP_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64"
    }

    private fun getBinaryPath(): String? {
        // Try nativeLibraryDir first (always executable on Android)
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeBin = File(nativeDir, "libytdlp.so")
        if (nativeBin.exists()) return nativeBin.absolutePath

        // Try filesDir (may work on older Android)
        val filesBin = File(context.filesDir, "yt-dlp")
        if (filesBin.exists() && filesBin.canExecute()) return filesBin.absolutePath

        // Download to filesDir
        return downloadBinary()
    }

    private fun downloadBinary(): String? {
        return try {
            Log.d(TAG, "Downloading yt-dlp binary...")
            val outFile = File(context.filesDir, "yt-dlp")
            URL(YTDLP_URL).openStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outFile.setExecutable(true, true)
            outFile.setReadable(true, false)
            Log.d(TAG, "yt-dlp downloaded to ${outFile.absolutePath}, size=${outFile.length()}")
            if (outFile.length() > 1000) outFile.absolutePath else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download yt-dlp", e)
            null
        }
    }

    suspend fun download(
        url: String,
        outputDir: String,
        quality: String = "1080",
        onProgress: (Int) -> Unit = {}
    ): DownloadResult {
        return try {
            val binaryPath = getBinaryPath()
                ?: return DownloadResult(false, error = "Could not get yt-dlp binary. Check internet connection.")

            val formatStr = when (quality) {
                "720"  -> "bestvideo[height<=720]+bestaudio/best[height<=720]"
                "480"  -> "bestvideo[height<=480]+bestaudio/best[height<=480]"
                else   -> "bestvideo[height<=1080]+bestaudio/best[height<=1080]"
            }

            val outputTemplate = "$outputDir/%(uploader)s_%(id)s.%(ext)s"

            val pb = ProcessBuilder(
                binaryPath,
                "--no-playlist",
                "--format", formatStr,
                "--merge-output-format", "mp4",
                "--output", outputTemplate,
                "--newline",
                url
            )
            pb.environment()["HOME"] = context.filesDir.absolutePath
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            pb.redirectErrorStream(true)

            Log.d(TAG, "Running yt-dlp: $binaryPath $url")
            val process = pb.start()
            var lastFileName: String? = null

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.lines().forEach { line ->
                Log.d(TAG, "yt-dlp: $line")
                if (line.contains("%")) {
                    Regex("""(\d+\.?\d*)%""").find(line)
                        ?.groupValues?.get(1)?.toFloatOrNull()
                        ?.let { onProgress(it.toInt()) }
                }
                if (line.contains("Destination:") || line.contains("[Merger]")) {
                    lastFileName = line.substringAfterLast("/").trim()
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                DownloadResult(true, fileName = lastFileName ?: "reel.mp4")
            } else {
                DownloadResult(false, error = "yt-dlp failed (code $exitCode). Try again.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception", e)
            DownloadResult(false, error = e.message)
        }
    }
}
