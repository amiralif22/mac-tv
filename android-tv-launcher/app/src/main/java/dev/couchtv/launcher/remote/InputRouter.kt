package dev.couchtv.launcher.remote

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.view.KeyEvent

/**
 * Turns remote messages into things the device does.
 *
 * Three routes, in order, because Android gives an app different levels of reach depending on
 * whether it is in front:
 *
 * 1. **The launcher itself.** While the home screen has focus, every key is dispatched straight into
 *    the activity, so the D-pad drives the launcher's own focus model exactly as a TV remote would.
 * 2. **The system.** Volume and mute always work ([AudioManager]); wake and sleep work through the
 *    power manager or, when the launcher is device owner, [DevicePolicyManager]. Back and Home fall
 *    back to the optional accessibility service.
 * 3. **Nothing.** If a key cannot be delivered, [handleKey] returns false and the remote shows
 *    "not available in this app" — never a silent failure.
 */
class InputRouter(private val context: Context) {

    /** Set by the activity: `true` when the launcher consumed the event. */
    @Volatile
    var localKeyHandler: ((KeyEvent) -> Boolean)? = null

    private val audio: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private val power: PowerManager? by lazy {
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    fun handleKey(key: Protocol.RemoteKey, hold: Boolean): Boolean = when (key) {
        Protocol.RemoteKey.VOLUME_UP -> adjustVolume(AudioManager.ADJUST_RAISE)
        Protocol.RemoteKey.VOLUME_DOWN -> adjustVolume(AudioManager.ADJUST_LOWER)
        Protocol.RemoteKey.MUTE -> toggleMute()
        Protocol.RemoteKey.WAKE -> wake()
        Protocol.RemoteKey.POWER, Protocol.RemoteKey.SLEEP -> toggleScreen()
        Protocol.RemoteKey.HOME -> dispatchLocal(KeyEvent.KEYCODE_HOME) ||
            (RemoteAccessibilityService.instance?.globalHome() ?: false)

        Protocol.RemoteKey.BACK -> dispatchLocal(KeyEvent.KEYCODE_BACK) ||
            (RemoteAccessibilityService.instance?.globalBack() ?: false)

        Protocol.RemoteKey.MENU -> dispatchLocal(KeyEvent.KEYCODE_MENU, hold) ||
            (RemoteAccessibilityService.instance?.globalQuickSettings() ?: false)

        else -> dispatchLocal(keyCodeFor(key), hold)
    }

    fun keyCodeFor(key: Protocol.RemoteKey): Int = when (key) {
        Protocol.RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
        Protocol.RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        Protocol.RemoteKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        Protocol.RemoteKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        Protocol.RemoteKey.OK -> KeyEvent.KEYCODE_DPAD_CENTER
        Protocol.RemoteKey.BACK -> KeyEvent.KEYCODE_BACK
        Protocol.RemoteKey.HOME -> KeyEvent.KEYCODE_HOME
        Protocol.RemoteKey.MENU -> KeyEvent.KEYCODE_MENU
        Protocol.RemoteKey.INFO -> KeyEvent.KEYCODE_INFO
        Protocol.RemoteKey.SETTINGS -> KeyEvent.KEYCODE_SETTINGS
        Protocol.RemoteKey.SEARCH -> KeyEvent.KEYCODE_SEARCH
        Protocol.RemoteKey.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
        Protocol.RemoteKey.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
        Protocol.RemoteKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        Protocol.RemoteKey.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        Protocol.RemoteKey.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
        Protocol.RemoteKey.FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        Protocol.RemoteKey.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
        Protocol.RemoteKey.PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        Protocol.RemoteKey.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
        Protocol.RemoteKey.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
        Protocol.RemoteKey.MUTE -> KeyEvent.KEYCODE_VOLUME_MUTE
        Protocol.RemoteKey.POWER, Protocol.RemoteKey.SLEEP -> KeyEvent.KEYCODE_POWER
        Protocol.RemoteKey.WAKE -> KeyEvent.KEYCODE_WAKEUP
    }

    private fun dispatchLocal(code: Int, hold: Boolean = false): Boolean {
        val handler = localKeyHandler ?: return false
        val down = KeyEvent(System.currentTimeMillis(), System.currentTimeMillis(), KeyEvent.ACTION_DOWN, code, 0)
        return try {
            val consumed = handler(down)
            if (!hold) {
                val up = KeyEvent(System.currentTimeMillis(), System.currentTimeMillis(), KeyEvent.ACTION_UP, code, 0)
                handler(up)
            }
            consumed
        } catch (error: RuntimeException) {
            // A stale activity reference must not take the remote down.
            false
        }
    }

    fun adjustVolume(direction: Int): Boolean = runCatching {
        audio?.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            direction,
            AudioManager.FLAG_SHOW_UI,
        )
        true
    }.getOrDefault(false)

    fun toggleMute(): Boolean = runCatching {
        val manager = audio ?: return@runCatching false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            manager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_TOGGLE_MUTE,
                AudioManager.FLAG_SHOW_UI,
            )
        } else {
            @Suppress("DEPRECATION")
            manager.setStreamMute(AudioManager.STREAM_MUSIC, !manager.isStreamMute(AudioManager.STREAM_MUSIC))
        }
        true
    }.getOrDefault(false)

    fun volumeFraction(): Float = runCatching {
        val manager = audio ?: return@runCatching 0.4f
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        manager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }.getOrDefault(0.4f)

    fun isMuted(): Boolean = runCatching {
        audio?.isStreamMute(AudioManager.STREAM_MUSIC) == true
    }.getOrDefault(false)

    fun isScreenOn(): Boolean = runCatching {
        power?.isInteractive ?: true
    }.getOrDefault(true)

    private fun toggleScreen(): Boolean = if (isScreenOn()) sleepDevice() else wake()

    /** Screen off. Needs device-owner rights; otherwise the remote is told it can't. */
    fun sleepDevice(): Boolean = runCatching {
        val policy = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            ?: return@runCatching false
        if (!policy.isAdminActive(ComponentName(context, RemoteDeviceAdmin::class.java))) return@runCatching false
        policy.lockNow()
        true
    }.getOrDefault(false)

    fun wake(): Boolean = runCatching {
        val manager = power ?: return@runCatching false
        val lock = manager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "CouchTv:remote-wake",
        )
        lock.acquire(5_000L)
        true
    }.getOrDefault(false)

    /** Typing into the launcher's search is handled by the UI; elsewhere, try the accessibility path. */
    fun insertText(text: String): Boolean =
        RemoteAccessibilityService.instance?.insertText(text) ?: false

    fun clearText(): Boolean = RemoteAccessibilityService.instance?.clearFocusedText() ?: false
}
