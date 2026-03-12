package com.reelsaver.util

import android.content.Context
import android.os.Environment

class Prefs(context: Context) {

    private val prefs = context.getSharedPreferences("reelsaver_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAVE_DIR = "save_directory"
        private const val KEY_QUALITY = "quality"
        private const val KEY_NOTIF_STYLE = "notif_style"
    }

    fun getSaveDirectory(): String {
        val default = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES
        ).absolutePath + "/ReelSaver"
        return prefs.getString(KEY_SAVE_DIR, default) ?: default
    }

    fun setSaveDirectory(path: String) =
        prefs.edit().putString(KEY_SAVE_DIR, path).apply()

    fun getQuality(): String =
        prefs.getString(KEY_QUALITY, "1080") ?: "1080"

    fun setQuality(q: String) =
        prefs.edit().putString(KEY_QUALITY, q).apply()

    fun getNotifStyle(): String =
        prefs.getString(KEY_NOTIF_STYLE, "silent") ?: "silent"

    fun setNotifStyle(style: String) =
        prefs.edit().putString(KEY_NOTIF_STYLE, style).apply()
}
