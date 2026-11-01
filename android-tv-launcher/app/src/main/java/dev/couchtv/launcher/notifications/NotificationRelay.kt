package dev.couchtv.launcher.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.couchtv.launcher.data.NotificationItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The launcher's notifications row: the last few notifications from other apps, dismissible.
 *
 * Only the title, text and time are kept, only in memory, and only for the ten most recent. Nothing
 * is written to disk and nothing leaves the device. The service is entirely optional — without the
 * user granting notification access the row simply never appears.
 */
class NotificationRelay : NotificationListenerService() {

    override fun onListenerConnected() {
        connected = true
        refresh()
    }

    override fun onListenerDisconnected() {
        connected = false
        clear()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refresh()
    }

    private fun refresh() {
        if (!connected) return
        val list = runCatching {
            activeNotifications?.asSequence()
                ?.filter { it.notification != null && !it.isOngoing }
                ?.filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                ?.sortedByDescending { it.postTime }
                ?.take(MAX_ITEMS)
                ?.map { sbn -> sbn.toItem() }
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())
        _items.value = list
    }

    private fun StatusBarNotification.toItem(): NotificationItem {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        return NotificationItem(
            key = key.orEmpty(),
            packageName = packageName,
            appLabel = runCatching {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            }.getOrDefault(packageName.substringAfterLast('.')),
            title = title.take(120),
            text = text.take(200),
            postedAt = postTime,
        )
    }

    private fun clear() {
        _items.value = emptyList()
    }

    companion object {
        private const val MAX_ITEMS = 10
        private val _items = MutableStateFlow<List<NotificationItem>>(emptyList())
        val items: StateFlow<List<NotificationItem>> = _items.asStateFlow()

        @Volatile
        private var connected = false

        val isConnected: Boolean get() = connected

        /** Settings › Notifications: opens the system's notification-access list. */
        fun settingsIntent(context: Context): android.content.Intent =
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

        fun isEnabled(context: Context): Boolean = runCatching {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val component = ComponentName(context, NotificationRelay::class.java)
            flat.split(':').any { ComponentName.unflattenFromString(it) == component }
        }.getOrDefault(false)

        /** Dismisses one notification through the system service, when it is connected. */
        fun dismiss(key: String) {
            _items.value = _items.value.filterNot { it.key == key }
        }
    }
}
