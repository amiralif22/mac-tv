package dev.couchtv.launcher.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.couchtv.launcher.MainActivity
import dev.couchtv.launcher.R

/**
 * Keeps the remote listening while another app (a streaming service, usually) has the screen.
 *
 * A foreground service with a low-importance notification is the honest way to hold a socket open on
 * modern Android. It is started only when the user has the remote switched on, and stopped the moment
 * they switch it off.
 */
class RemoteService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        val port = intent?.getIntExtra(EXTRA_PORT, RemoteHub.DEFAULT_WS_PORT) ?: RemoteHub.DEFAULT_WS_PORT
        val started = RemoteHub.start(applicationContext, port)
        if (!started) {
            // No listener means nothing to keep alive; stop rather than sit in the notification shade.
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        RemoteHub.stop()
        super.onDestroy()
    }

    private fun buildNotification(): android.app.Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0),
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.settings_remote_body))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(android.app.Notification.PRIORITY_MIN)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notifications_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = getString(R.string.settings_remote_body)
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "remote"
        private const val NOTIFICATION_ID = 0x0C0D
        private const val EXTRA_PORT = "port"

        fun start(context: Context, port: Int = RemoteHub.DEFAULT_WS_PORT) {
            val intent = Intent(context, RemoteService::class.java).putExtra(EXTRA_PORT, port)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, RemoteService::class.java)) }
        }
    }
}
