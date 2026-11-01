package dev.couchtv.launcher.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.couchtv.launcher.MainActivity

/**
 * "Start on boot": if the user asked for it in Settings, bring the launcher up as soon as the device
 * has finished starting. It is the user's choice, off by default, and it only ever opens the
 * launcher — never a second activity or a service.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val enabled = runCatching {
            context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART, false)
        }.getOrDefault(false)
        if (!enabled) return

        val launch = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(launch) }
    }

    companion object {
        const val KEY_AUTOSTART = "autostart"
    }
}
