package dev.couchtv.launcher

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.couchtv.launcher.remote.Protocol
import dev.couchtv.launcher.ui.Direction
import dev.couchtv.launcher.ui.LauncherRoot
import dev.couchtv.launcher.ui.LauncherViewModel
import dev.couchtv.launcher.ui.Screen
import dev.couchtv.launcher.ui.UiKeys
import dev.couchtv.launcher.ui.theme.CouchTheme
import dev.couchtv.launcher.util.DeviceProfile
import dev.couchtv.launcher.util.applyTvSafeInsets

/**
 * The launcher's only activity.
 *
 * It draws the Compose tree, hides the system bars (a home screen should not look like an app), and
 * owns the one key pipeline: screens that register a [UiKeys] handler get first refusal, then the
 * activity's own D-pad handling drives the view model, and anything left goes to the system.
 *
 * It is a HOME activity, so Android starts it on boot, on Home, and when the user picks Couch TV as
 * their default launcher.
 */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val metrics = resources.displayMetrics
        vm.setScreenSize(metrics.widthPixels, metrics.heightPixels)

        // Remote keys are dispatched as if the device's own remote sent them.
        vm.inputRouter.localKeyHandler = { event -> superDispatch(event) }

        val profile = DeviceProfile.of(this)
        // A TV crops the edges of the picture; keep the launcher's content inside the safe area.
        if (profile.kind == DeviceProfile.Kind.LEANBACK) window.decorView.applyTvSafeInsets()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            CouchTheme(mode = state.settings.theme, profile = profile) {
                LauncherRoot(vm, state)
            }
        }
        handleIntent(intent)
    }

    /**
     * Bypasses this activity's own handling: used when the *phone* sends a media key.
     *
     * `super.dispatchKeyEvent` is the platform Activity implementation we want; androidx.core's
     * override is annotated @RestrictTo, so lint flags the call even from a subclass.
     */
    @SuppressLint("RestrictedApi")
    private fun superDispatch(event: KeyEvent): Boolean = super.dispatchKeyEvent(event)

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_OPEN_SETTINGS) vm.openScreen(Screen.SETTINGS)
    }

    override fun onResume() {
        super.onResume()
        handleIntent(intent)
        vm.onResume()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        vm.noteActivity()
    }

    @SuppressLint("RestrictedApi") // See superDispatch(): the restricted super class is ours.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // A grid, a form or search field takes the keys for as long as it is on screen.
        UiKeys.handler?.let { handler -> if (handler(event)) return true }

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (handleNavigationKey(event)) return true
        } else if (event.action == KeyEvent.ACTION_UP && isNavigationKey(event.keyCode)) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isNavigationKey(code: Int): Boolean = when (code) {
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU -> true

        else -> false
    }

    private fun handleNavigationKey(event: KeyEvent): Boolean = when (event.keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> { vm.moveFocus(Direction.UP); true }
        KeyEvent.KEYCODE_DPAD_DOWN -> { vm.moveFocus(Direction.DOWN); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { vm.moveFocus(Direction.LEFT); true }
        KeyEvent.KEYCODE_DPAD_RIGHT -> { vm.moveFocus(Direction.RIGHT); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            vm.activateFocus(); true
        }

        KeyEvent.KEYCODE_BACK -> {
            // On the home screen Back is inert: there is nowhere further back to go.
            vm.back()
            true
        }

        KeyEvent.KEYCODE_MENU -> { vm.toggleQuickSettings(); true }
        KeyEvent.KEYCODE_SEARCH -> { vm.openSearch(); true }
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> vm.onKey(Protocol.RemoteKey.PLAY_PAUSE, false)
        KeyEvent.KEYCODE_MEDIA_PLAY -> vm.onKey(Protocol.RemoteKey.PLAY, false)
        KeyEvent.KEYCODE_MEDIA_PAUSE -> vm.onKey(Protocol.RemoteKey.PAUSE, false)
        KeyEvent.KEYCODE_MEDIA_NEXT -> vm.onKey(Protocol.RemoteKey.NEXT, false)
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> vm.onKey(Protocol.RemoteKey.PREV, false)
        KeyEvent.KEYCODE_MEDIA_REWIND -> vm.onKey(Protocol.RemoteKey.REWIND, false)
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> vm.onKey(Protocol.RemoteKey.FORWARD, false)
        KeyEvent.KEYCODE_VOLUME_UP -> vm.onKey(Protocol.RemoteKey.VOLUME_UP, false)
        KeyEvent.KEYCODE_VOLUME_DOWN -> vm.onKey(Protocol.RemoteKey.VOLUME_DOWN, false)
        KeyEvent.KEYCODE_VOLUME_MUTE -> vm.onKey(Protocol.RemoteKey.MUTE, false)
        else -> false
    }

    /** Long-press Home opens quick settings, like a TV's own control centre. */
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_HOME) {
            vm.toggleQuickSettings()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    companion object {
        const val ACTION_OPEN_SETTINGS = "dev.couchtv.launcher.OPEN_SETTINGS"
    }
}
