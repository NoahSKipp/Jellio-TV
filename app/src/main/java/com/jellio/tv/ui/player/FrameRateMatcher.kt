package com.jellio.tv.ui.player

import android.app.Activity
import android.os.Build
import android.view.Display
import kotlin.math.abs
import kotlin.math.roundToInt

// Matches the TV's refresh rate to the video, the way Nuvio's frame rate
// matching does: a mode with the same resolution whose refresh rate is a
// whole multiple of the frame rate (24 Hz or 48 Hz for 23.976 fps films,
// 50 Hz for 25 fps), so motion plays without the uneven 3:2 judder of 24 fps
// on 60 Hz. The mode the TV had before is put back afterwards.
object FrameRateMatcher {
    private const val TOLERANCE = 0.01f
    private var originalModeId: Int? = null

    // A measured rate snapped to the standard rate it stands for.
    fun snap(fps: Float): Float {
        val standards = floatArrayOf(23.976f, 24f, 25f, 29.97f, 30f, 47.952f, 48f, 50f, 59.94f, 60f)
        return standards.minByOrNull { abs(it - fps) }?.takeIf { abs(it - fps) < 0.2f } ?: fps
    }

    fun apply(activity: Activity, fps: Float) {
        if (fps <= 0f || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = activity.windowManager.defaultDisplay ?: return
        val current = display.mode
        val target = snap(fps)
        val sameSize = display.supportedModes.filter {
            it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
        }
        val best = sameSize
            .mapNotNull { mode -> multipleError(mode, target)?.let { mode to it } }
            .sortedWith(compareBy<Pair<Display.Mode, Float>> { it.second }.thenBy { it.first.refreshRate })
            .firstOrNull()?.first ?: return
        if (best.modeId == current.modeId) return
        if (originalModeId == null) originalModeId = current.modeId
        val attrs = activity.window.attributes
        attrs.preferredDisplayModeId = best.modeId
        activity.window.attributes = attrs
    }

    fun restore(activity: Activity) {
        val original = originalModeId ?: return
        originalModeId = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val attrs = activity.window.attributes
        attrs.preferredDisplayModeId = original
        activity.window.attributes = attrs
    }

    // How far a mode's refresh rate is from a whole multiple of the frame
    // rate, as a fraction; null when it isn't one.
    private fun multipleError(mode: Display.Mode, fps: Float): Float? {
        val ratio = mode.refreshRate / fps
        val whole = ratio.roundToInt()
        if (whole < 1) return null
        val error = abs(ratio - whole) / whole
        return error.takeIf { it <= TOLERANCE }
    }
}
