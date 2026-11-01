package dev.couchtv.launcher.ui

import android.view.KeyEvent

/**
 * One place where a screen can claim the hardware keys for as long as it is visible.
 *
 * The home screen does not register anything: the activity routes the D-pad into the view model,
 * which owns focus there. A grid, a form or a search field needs its own arithmetic, so it registers
 * a handler here while it is on screen and hands it back on dispose.
 */
object UiKeys {
    @Volatile
    var handler: ((KeyEvent) -> Boolean)? = null

    /** Convenience for handlers that only care about key-down. */
    fun down(event: KeyEvent, block: (Int) -> Boolean): Boolean =
        event.action == KeyEvent.ACTION_DOWN && block(event.keyCode)
}
