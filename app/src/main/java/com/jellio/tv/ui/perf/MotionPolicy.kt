package com.jellio.tv.ui.perf

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// How much ambient motion (the seasonal scenes) this device gets. FULL
// redraws every frame, REDUCED at a lower rate, STILL not at all.
enum class MotionLevel { FULL, REDUCED, STILL }

// What the device looks like, worked out once at start: low-RAM devices,
// two cores or a small heap struggle with full-screen redraws, and a UI
// drawn above 1080p (4K output) costs about four times as much per frame.
data class DeviceProfile(
    val cores: Int,
    val heapMb: Int,
    val lowRam: Boolean,
    val uiWidth: Int,
    val uiHeight: Int,
    val suggested: MotionLevel,
) {
    fun describe(): String =
        "${uiWidth}x$uiHeight UI, $cores cores, ${heapMb} MB app memory${if (lowRam) ", low-RAM device" else ""}"
}

object MotionPolicy {
    private const val PREFS = "jellio_display"
    private const val KEY = "motion"

    // "auto", "full", "reduced" or "off".
    var preference by mutableStateOf("auto")
        private set

    var profile by mutableStateOf<DeviceProfile?>(null)
        private set

    private var systemStill = false

    fun init(context: Context) {
        if (profile != null) return
        val app = context.applicationContext
        preference = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "auto") ?: "auto"
        systemStill = runCatching {
            Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        profile = detect(app).also { Log.i("JellioPerf", "Device: ${it.describe()} -> ${it.suggested}") }
    }

    fun setPreference(context: Context, value: String) {
        preference = value
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }

    val level: MotionLevel
        get() = when {
            systemStill -> MotionLevel.STILL
            preference == "full" -> MotionLevel.FULL
            preference == "reduced" -> MotionLevel.REDUCED
            preference == "off" -> MotionLevel.STILL
            else -> profile?.suggested ?: MotionLevel.FULL
        }

    private fun detect(context: Context): DeviceProfile {
        val am = context.getSystemService(ActivityManager::class.java)
        val lowRam = am?.isLowRamDevice ?: false
        val heapMb = am?.memoryClass ?: 256
        val cores = Runtime.getRuntime().availableProcessors()
        val metrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels)
        val height = minOf(metrics.widthPixels, metrics.heightPixels)
        val suggested = when {
            lowRam || cores <= 2 || heapMb < 160 -> MotionLevel.REDUCED
            width.toLong() * height > 2560L * 1440L -> MotionLevel.REDUCED
            else -> MotionLevel.FULL
        }
        return DeviceProfile(cores, heapMb, lowRam, width, height, suggested)
    }
}
