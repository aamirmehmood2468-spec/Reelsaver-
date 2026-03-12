package com.reelsaver.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textview.MaterialTextView
import com.reelsaver.R
import com.reelsaver.util.Prefs

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var tvSaveDir: MaterialTextView
    private lateinit var cgQuality: ChipGroup

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // Persist permission
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val path = getRealPathFromUri(uri) ?: uri.toString()
            prefs.setSaveDirectory(path)
            tvSaveDir.text = path
            Toast.makeText(this, "Save folder updated ✓", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this,
                "Notification permission needed to show download progress",
                Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs(this)
        tvSaveDir = findViewById(R.id.tvSaveDir)
        cgQuality = findViewById(R.id.cgQuality)

        // Show current save dir
        tvSaveDir.text = prefs.getSaveDirectory()

        // Quality chip selection
        when (prefs.getQuality()) {
            "1080" -> cgQuality.check(R.id.chip1080)
            "720"  -> cgQuality.check(R.id.chip720)
            "480"  -> cgQuality.check(R.id.chip480)
        }
        cgQuality.setOnCheckedStateChangeListener { _, checkedIds ->
            val q = when (checkedIds.firstOrNull()) {
                R.id.chip720 -> "720"
                R.id.chip480 -> "480"
                else         -> "1080"
            }
            prefs.setQuality(q)
        }

        // Choose folder button
        findViewById<MaterialButton>(R.id.btnChooseFolder).setOnClickListener {
            folderPicker.launch(null)
        }

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Show how to use
        updateStatusCard()
    }

    private fun updateStatusCard() {
        val tv = findViewById<MaterialTextView>(R.id.tvStatus)
        tv.text = "✅ ReelSaver is active!\n\n" +
                "How to use:\n" +
                "1. Open any reel on Instagram\n" +
                "2. Tap Share → More Options\n" +
                "3. Tap ReelSaver\n" +
                "4. Stay on Instagram — reel downloads silently\n" +
                "5. Check notification bar for progress\n\n" +
                "Save location:\n${prefs.getSaveDirectory()}"
    }

    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
            val split = docId.split(":")
            if (split.size >= 2) {
                val type = split[0]
                val path = split[1]
                if (type.equals("primary", ignoreCase = true)) {
                    "${Environment.getExternalStorageDirectory()}/$path"
                } else {
                    "/storage/$type/$path"
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
