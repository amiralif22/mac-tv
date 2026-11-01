package dev.couchtv.launcher.ui

import dev.couchtv.launcher.apk.ApkInspector
import dev.couchtv.launcher.data.AppEntry
import dev.couchtv.launcher.data.NotificationItem
import dev.couchtv.launcher.streaming.MediaItem

/** Which full screen the launcher is showing. Overlays sit on top of [HOME]. */
enum class Screen { HOME, MANAGE_APPS, SETTINGS, SEARCH, SIDELOAD, PAIRING }

/** A floating layer over the home screen. */
enum class Overlay { QUICK_SETTINGS, SCREENSAVER }

/** One thing in a home row. The focus engine only cares how many there are. */
sealed interface HomeItem {
    val id: String
    val title: String

    data class App(val entry: AppEntry) : HomeItem {
        override val id: String get() = "app:${entry.packageName}"
        override val title: String get() = entry.label
    }

    data class Media(val item: MediaItem) : HomeItem {
        override val id: String get() = "media:${item.id}"
        override val title: String get() = item.title
    }

    data class Action(val action: String, override val title: String) : HomeItem {
        override val id: String get() = "action:$action"
    }

    data class Notice(val item: NotificationItem) : HomeItem {
        override val id: String get() = "notice:${item.key}"
        override val title: String get() = item.title.ifBlank { item.appLabel }
    }
}

enum class RowKind { STATUS, FAVORITES, CONTINUE, TRENDING, APPS, MOVIES, SERIES, NOTIFICATIONS }

/** A horizontal row on the home screen. A null title draws no header (the status row). */
data class HomeRow(
    val id: String,
    val title: String?,
    val kind: RowKind,
    val items: List<HomeItem>,
)

/** A PIN is needed before this app opens. */
data class ParentalRequest(val app: AppEntry)

/** After an install: "Add [App] to your home screen?" */
data class AddPrompt(val entry: AppEntry)

/** "This app may not support remote control. Install an Android TV version instead?" */
data class CompatPrompt(val apk: ApkInspector.ApkInfo)

/** A newly detected app, offered in a quiet banner under the header. */
data class NewAppNotice(val entry: AppEntry)
