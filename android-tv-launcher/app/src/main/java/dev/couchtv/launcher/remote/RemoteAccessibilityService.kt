package dev.couchtv.launcher.remote

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional helper that lets a paired phone act *inside other apps*: tap where the cursor is, scroll,
 * go Back or Home, and type into whatever field is focused.
 *
 * Android does not let an ordinary app synthesise D-pad key events for another app, but an
 * accessibility service may dispatch gestures and perform global actions — which covers the parts of
 * the remote that matter once a streaming app is in the foreground.
 *
 * The service reads nothing: no window text is copied anywhere, no event is stored, and it is inert
 * unless the user turns it on and a phone is connected.
 */
class RemoteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** A finger tap at a screen coordinate (pixels). */
    fun tap(x: Float, y: Float, durationMs: Long = 60L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 0L, durationMs)
    }

    /** A press-and-hold, for context menus. */
    fun longPress(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(path, 0L, 650L)
    }

    /** A vertical flick, standing in for a mouse wheel. */
    fun swipe(x: Float, fromY: Float, deltaY: Float): Boolean {
        val path = Path().apply {
            moveTo(x, fromY)
            lineTo(x, fromY + deltaY)
        }
        return dispatch(path, 0L, 180L)
    }

    private fun dispatch(path: Path, start: Long, duration: Long): Boolean = runCatching {
        val stroke = GestureDescription.StrokeDescription(path, start, duration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }.getOrDefault(false)

    fun globalBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)

    fun globalHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)

    fun globalNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    fun globalQuickSettings(): Boolean = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)

    /** Types into the focused field of another app, if it accepts text. */
    fun insertText(text: String): Boolean {
        val node = findFocusedEditable() ?: return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) }
            .getOrDefault(false)
    }

    fun clearFocusedText(): Boolean {
        val node = findFocusedEditable() ?: return false
        return runCatching {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            })
        }.getOrDefault(false)
    }

    /** The foreground app's label, for the remote's "now controlling" line. */
    fun foregroundPackage(): String? = runCatching {
        val root = rootInActiveWindow ?: return null
        root.packageName?.toString()
    }.getOrNull()

    private fun findFocusedEditable(): AccessibilityNodeInfo? = runCatching {
        val root = rootInActiveWindow ?: return null
        // Depth-first from the root, bounded so a pathological tree cannot stall the remote.
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.addLast(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val current = queue.removeFirst()
            visited++
            if (current.isEditable && current.isFocused) return current
            for (index in 0 until current.childCount) {
                current.getChild(index)?.let(queue::addLast)
            }
        }
        null
    }.getOrNull()

    companion object {
        @Volatile
        var instance: RemoteAccessibilityService? = null
            private set

        val isConnected: Boolean get() = instance != null
    }
}
