package dev.couchtv.launcher.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import dev.couchtv.launcher.BuildConfig
import dev.couchtv.launcher.data.ThemeMode
import dev.couchtv.launcher.notifications.NotificationRelay
import dev.couchtv.launcher.ui.LauncherViewModel
import dev.couchtv.launcher.ui.Screen
import dev.couchtv.launcher.ui.UiKeys
import dev.couchtv.launcher.ui.cursorTarget
import dev.couchtv.launcher.ui.components.PillButton
import dev.couchtv.launcher.ui.components.glass
import dev.couchtv.launcher.ui.components.panel
import dev.couchtv.launcher.ui.theme.tvDp
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.CouchType
import dev.couchtv.launcher.ui.theme.LocalPalette
import dev.couchtv.launcher.ui.theme.backdropBrush
import dev.couchtv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * Settings, and the three small screens it leads to (Manage apps, Install an APK, Phone remote).
 *
 * The list is a single column with one focus index, so up and down always work; left and right change
 * the value in place (a toggle, or the next option) exactly as a TV settings screen should.
 */

private data class SettingItem(
    val id: String,
    val label: String,
    val value: String? = null,
    val subtitle: String? = null,
    val section: String? = null,
    val action: () -> Unit,
    val adjust: ((Int) -> Unit)? = null,
)

@Composable
fun SettingsScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    var showNuvio by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }

    if (showNuvio) {
        NuvioConnectScreen(vm, state) { showNuvio = false }
        return
    }
    if (showPin) {
        PinEntryScreen(title = stringResource(R.string.settings_parental_set_pin), onCancel = { showPin = false }) { pin ->
            vm.setParentalPin(pin)
            showPin = false
        }
        return
    }

    val settings = state.settings
    val items = buildList {
        add(
            SettingItem(
                id = "theme",
                label = stringResource(R.string.settings_theme),
                value = when (settings.theme) {
                    ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                },
                section = stringResource(R.string.settings_appearance),
                action = {
                    val next = when (settings.theme) {
                        ThemeMode.DARK -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.SYSTEM
                        ThemeMode.SYSTEM -> ThemeMode.DARK
                    }
                    vm.setTheme(next)
                },
                adjust = { direction ->
                    val order = listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM)
                    val index = order.indexOf(settings.theme)
                    vm.setTheme(order[(index + direction + order.size) % order.size])
                },
            )
        )
        add(
            SettingItem(
                id = "saver",
                label = stringResource(R.string.settings_screensaver),
                value = "${settings.saverDelayMinutes} min",
                subtitle = stringResource(R.string.settings_screensaver_hint),
                action = {},
                adjust = { direction ->
                    val options = listOf(2, 5, 10, 15, 30)
                    val index = options.indexOf(settings.saverDelayMinutes).coerceAtLeast(0)
                    vm.setSaverDelay(options[(index + direction + options.size) % options.size])
                },
            )
        )
        add(
            SettingItem(
                id = "clock",
                label = stringResource(R.string.settings_clock_on_saver),
                value = stringResource(if (settings.saverClock) R.string.on else R.string.off),
                action = { vm.setSaverClock(!settings.saverClock) },
                adjust = { vm.setSaverClock(!settings.saverClock) },
            )
        )
        add(
            SettingItem(
                id = "apps",
                label = stringResource(R.string.settings_apps),
                subtitle = stringResource(R.string.settings_apps_body),
                section = stringResource(R.string.settings_home),
                action = { vm.openScreen(Screen.MANAGE_APPS) },
            )
        )
        add(
            SettingItem(
                id = "sideload",
                label = stringResource(R.string.sideload),
                subtitle = stringResource(R.string.sideload_hint),
                action = { vm.openSideload() },
            )
        )
        add(
            SettingItem(
                id = "remote",
                label = stringResource(R.string.settings_remote),
                value = if (settings.remoteEnabled) "On" else "Off",
                subtitle = if (state.remoteClients > 0) {
                    "${state.remoteClients} connected"
                } else {
                    stringResource(R.string.settings_remote_body)
                },
                section = stringResource(R.string.settings_remote),
                action = { vm.setRemoteEnabled(!settings.remoteEnabled) },
                adjust = { vm.setRemoteEnabled(!settings.remoteEnabled) },
            )
        )
        add(
            SettingItem(
                id = "pairing",
                label = stringResource(R.string.settings_pair_code),
                value = state.pairingCode,
                subtitle = stringResource(R.string.settings_pair_hint),
                action = { vm.openPairing() },
            )
        )
        add(
            SettingItem(
                id = "nuvio",
                label = stringResource(R.string.settings_nuvio),
                value = settings.nuvioAccount ?: stringResource(R.string.not_connected),
                subtitle = stringResource(R.string.settings_nuvio_body),
                section = stringResource(R.string.category_streaming),
                action = if (settings.nuvioAccount != null) {
                    { vm.disconnectNuvio() }
                } else {
                    { showNuvio = true }
                },
            )
        )
        add(
            SettingItem(
                id = "refresh",
                label = stringResource(R.string.settings_nuvio_refresh),
                action = { vm.refreshStreaming() },
            )
        )
        add(
            SettingItem(
                id = "parental",
                label = stringResource(R.string.settings_parental_enabled),
                value = if (settings.parentalEnabled) "On" else "Off",
                subtitle = stringResource(R.string.settings_parental_body),
                section = stringResource(R.string.settings_parental),
                action = { vm.setParentalEnabled(!settings.parentalEnabled) },
                adjust = { vm.setParentalEnabled(!settings.parentalEnabled) },
            )
        )
        add(
            SettingItem(
                id = "pin",
                label = if (settings.parentalEnabled) stringResource(R.string.settings_parental_change_pin) else stringResource(R.string.settings_parental_set_pin),
                action = { showPin = true },
            )
        )
        add(
            SettingItem(
                id = "autostart",
                label = stringResource(R.string.settings_autostart),
                value = if (settings.autostart) "On" else "Off",
                action = { vm.setAutostart(!settings.autostart) },
                adjust = { vm.setAutostart(!settings.autostart) },
            )
        )
        add(
            SettingItem(
                id = "notifications",
                label = stringResource(R.string.settings_notifications),
                value = if (settings.notificationsRow) "On" else "Off",
                subtitle = if (NotificationRelay.isEnabled(context)) {
                    stringResource(R.string.settings_notifications_body)
                } else {
                    stringResource(R.string.settings_notifications_hint)
                },
                section = stringResource(R.string.row_notifications),
                action = {
                    if (NotificationRelay.isEnabled(context)) {
                        vm.setNotificationsRow(!settings.notificationsRow)
                    } else {
                        runCatching { context.startActivity(NotificationRelay.settingsIntent(context)) }
                    }
                },
                adjust = { vm.setNotificationsRow(!settings.notificationsRow) },
            )
        )
        add(
            SettingItem(
                id = "network",
                label = stringResource(R.string.settings_network),
                value = state.network.label,
                subtitle = state.network.address?.let { "This TV is at $it" },
                section = stringResource(R.string.settings_about),
                action = { vm.refreshNetwork() },
            )
        )
        add(
            SettingItem(
                id = "version",
                label = stringResource(R.string.app_name),
                value = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                subtitle = stringResource(R.string.settings_about_body),
                action = {},
            )
        )
    }

    var focus by remember(items) { mutableStateOf(0) }
    val listState = rememberLazyListState()
    LaunchedEffect(focus) { listState.animateScrollToItem(focus) }

    DisposableEffect(items) {
        UiKeys.handler = { event ->
            UiKeys.down(event) { code ->
                when (code) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        focus = (focus - 1).coerceAtLeast(0); true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        focus = (focus + 1).coerceAtMost(items.lastIndex); true
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        items[focus].adjust?.invoke(-1); true
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        items[focus].adjust?.invoke(1); true
                    }

                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        items[focus].action(); true
                    }

                    else -> false
                }
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = tvDp(96f), end = tvDp(96f), top = tvDp(56f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.settings), style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.text))
                Spacer(Modifier.weight(1f))
                PillButton(label = stringResource(R.string.back), focused = false, onClick = { vm.back() })
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = tvDp(96f), end = tvDp(96f), top = tvDp(28f), bottom = tvDp(64f),
                ),
                verticalArrangement = Arrangement.spacedBy(tvDp(10f)),
            ) {
                items(items.size) { index ->
                    val item = items[index]
                    if (item.section != null) {
                        Text(
                            text = item.section,
                            style = CouchType.style(CouchType.caption2, FontWeight.Bold, palette.textFaint),
                            modifier = Modifier.padding(top = tvDp(22f), bottom = tvDp(6f)),
                        )
                    }
                    SettingRow(
                        item = item,
                        focused = index == focus,
                        onClick = {
                            focus = index
                            item.action()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItem, focused: Boolean, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(tvDp(20f))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) palette.focusPlatter else palette.glass)
            .border(
                tvDp(2f),
                if (focused) Color.Transparent else palette.hairline,
                shape,
            )
            .padding(horizontal = tvDp(32f), vertical = tvDp(18f))
            .cursorTargetSafe(item.id, onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = CouchType.style(
                    CouchType.caption1,
                    FontWeight.Medium,
                    if (focused) palette.focusText else palette.text,
                ),
            )
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    style = CouchType.style(
                        CouchType.caption2,
                        FontWeight.Normal,
                        if (focused) palette.focusText.copy(alpha = 0.7f) else palette.textDim,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (item.value != null) {
            Text(
                text = item.value,
                style = CouchType.style(
                    CouchType.caption1,
                    FontWeight.Bold,
                    if (focused) palette.focusText else palette.accent,
                ),
            )
        }
    }
}

/** Pointer support is optional here; the settings list is driven by the D-pad and touch. */
@Composable
private fun Modifier.cursorTargetSafe(id: String, onClick: () -> Unit): Modifier {
    val registry = dev.couchtv.launcher.ui.LocalCursorRegistry.current ?: return this
    return this.then(
        Modifier.cursorTarget(
            id = "setting:$id",
            onFocus = {},
            onActivate = onClick,
        )
    )
}

// ---- Install an APK -------------------------------------------------------------------------------

@Composable
fun SideloadScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::installFromUri)
    }

    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = tvDp(96f), vertical = tvDp(56f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sideload), style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.text))
                Spacer(Modifier.weight(1f))
                PillButton(label = stringResource(R.string.back), focused = false, onClick = { vm.back() })
            }
            Spacer(Modifier.height(tvDp(12f)))
            Text(
                text = stringResource(R.string.sideload_hint),
                style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
            )
            Spacer(Modifier.height(tvDp(36f)))

            Row(horizontalArrangement = Arrangement.spacedBy(tvDp(28f))) {
                Panel(title = stringResource(R.string.sideload_from_device)) {
                    PillButton(
                        label = stringResource(R.string.sideload_pick),
                        focused = false,
                        prominent = true,
                        onClick = {
                            runCatching { picker.launch(arrayOf("application/vnd.android.package-archive", "*/*")) }
                        },
                    )
                }
                Panel(title = stringResource(R.string.sideload_upload_title)) {
                    Text(
                        text = state.uploadUrl ?: stringResource(R.string.settings_network_none),
                        style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.accent),
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(tvDp(8f)))
                    Text(
                        text = stringResource(R.string.sideload_upload_body),
                        style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.textDim),
                    )
                }
            }

            state.installStatus?.let { status ->
                Spacer(Modifier.height(tvDp(28f)))
                Row(
                    Modifier
                        .glass(palette, RoundedCornerShape(CouchShapes.panel))
                        .padding(horizontal = tvDp(28f), vertical = tvDp(18f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status,
                        style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.text),
                    )
                    Spacer(Modifier.width(tvDp(20f)))
                    PillButton(label = stringResource(R.string.dismiss), focused = false, onClick = vm::clearInstallStatus)
                }
            }

            Spacer(Modifier.height(tvDp(28f)))
            Text(
                text = stringResource(R.string.sideload_permission_hint),
                style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.textFaint),
            )
        }
    }

    if (state.permissionNeeded) {
        // A small confirm panel; the system screen is the only place this can be granted.
        androidx.compose.ui.window.Dialog(onDismissRequest = vm::requestInstallPermission) {
            Column(
                Modifier
                    .panel(palette, RoundedCornerShape(CouchShapes.panel))
                    .padding(tvDp(32f)),
            ) {
                Text(
                    stringResource(R.string.sideload_allow),
                    style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.text),
                )
                Spacer(Modifier.height(tvDp(10f)))
                Text(
                    stringResource(R.string.sideload_permission_body),
                    style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
                )
                Spacer(Modifier.height(tvDp(20f)))
                PillButton(
                    label = stringResource(R.string.sideload_open_settings),
                    focused = true,
                    onClick = vm::requestInstallPermission,
                )
            }
        }
    }
}

// ---- Phone remote ----------------------------------------------------------------------------------

@Composable
fun PairingScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val code = state.pairingCode.chunked(3).joinToString(" ")

    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = tvDp(96f), vertical = tvDp(56f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.pair_title),
                    style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.text),
                )
                Spacer(Modifier.weight(1f))
                PillButton(label = stringResource(R.string.back), focused = false, onClick = { vm.back() })
            }
            Spacer(Modifier.height(tvDp(16f)))
            Text(
                text = stringResource(R.string.pair_body),
                style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
            )
            Spacer(Modifier.height(tvDp(40f)))

            Row(horizontalArrangement = Arrangement.spacedBy(tvDp(32f))) {
                Panel(title = stringResource(R.string.settings_pair_code)) {
                    Text(
                        text = code,
                        style = CouchType.style(CouchType.title1, FontWeight.Bold, palette.text),
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = stringResource(R.string.settings_pair_rotation),
                        style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.textDim),
                    )
                }
                Panel(title = stringResource(R.string.settings_this_tv)) {
                    Text(
                        text = state.network.address?.let { "$it:${dev.couchtv.launcher.remote.RemoteHub.wsPort.value}" }
                            ?: stringResource(R.string.settings_network_pending),
                        style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.accent),
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(tvDp(6f)))
                    Text(
                        text = if (state.remoteRunning) {
                            "Remote server on · ${state.remoteClients} connected"
                        } else {
                            stringResource(R.string.settings_remote_off)
                        },
                        style = CouchType.style(CouchType.caption2, FontWeight.Medium, palette.textDim),
                    )
                }
            }

            Spacer(Modifier.height(tvDp(36f)))
            Row(horizontalArrangement = Arrangement.spacedBy(tvDp(18f))) {
                PillButton(label = stringResource(R.string.settings_pair_new_code), focused = false, onClick = vm::newPairingCode)
                PillButton(label = stringResource(R.string.settings_pair_revoke), focused = false, onClick = vm::revokeDevices)
                PillButton(
                    label = if (state.settings.remoteEnabled) stringResource(R.string.settings_remote_off_action) else stringResource(R.string.settings_remote_on_action),
                    focused = false,
                    onClick = { vm.setRemoteEnabled(!state.settings.remoteEnabled) },
                )
            }
            Spacer(Modifier.height(tvDp(24f)))
            Text(
                text = stringResource(R.string.settings_remote_hint),
                style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.textFaint),
            )
        }
    }
}

// ---- Nuvio sign-in ---------------------------------------------------------------------------------

@Composable
private fun NuvioConnectScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState, onClose: () -> Unit) {
    val palette = LocalPalette.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var focus by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        UiKeys.handler = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> { focus = (focus - 1).coerceAtLeast(0); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { focus = (focus + 1).coerceAtMost(2); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (focus == 2) vm.connectNuvio(email.trim(), password)
                        true
                    }
                    KeyEvent.KEYCODE_DEL -> {
                        if (focus == 0) email = email.dropLast(1) else if (focus == 1) password = password.dropLast(1)
                        true
                    }
                    else -> {
                        val char = event.unicodeChar.toChar()
                        if (!char.isISOControl() && char != '\n') {
                            when (focus) {
                                0 -> email = (email + char).take(80)
                                1 -> password = (password + char).take(80)
                            }
                            true
                        } else {
                            false
                        }
                    }
                }
            } else {
                false
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = tvDp(96f), vertical = tvDp(56f)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_nuvio), style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.text))
                Spacer(Modifier.weight(1f))
                PillButton(label = stringResource(R.string.back), focused = false, onClick = onClose)
            }
            Spacer(Modifier.height(tvDp(12f)))
            Text(
                text = stringResource(R.string.settings_nuvio_hint),
                style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
                modifier = Modifier.fillMaxWidth(0.75f),
            )
            Spacer(Modifier.height(tvDp(36f)))

            Field(stringResource(R.string.settings_nuvio_email), email, focus == 0)
            Spacer(Modifier.height(tvDp(16f)))
            Field(stringResource(R.string.settings_nuvio_password), "*".repeat(password.length), focus == 1, secret = true)
            Spacer(Modifier.height(tvDp(28f)))
            PillButton(
                label = stringResource(R.string.settings_nuvio_connect),
                focused = focus == 2,
                prominent = true,
                onClick = { vm.connectNuvio(email.trim(), password) },
            )
            if (state.settings.nuvioAccount != null) {
                Spacer(Modifier.height(tvDp(20f)))
                Text(
                    text = "Connected as ${state.settings.nuvioAccount}",
                    style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.accent),
                )
            }
        }
    }
}

/** A fake text field: the caret is the focus, so a D-pad, a keyboard and the phone all work. */
@Composable
private fun Field(label: String, value: String, focused: Boolean, secret: Boolean = false) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(tvDp(18f))
    Column(
        Modifier
            .width(tvDp(1100f))
            .clip(shape)
            .background(palette.glass)
            .border(tvDp(2f), if (focused) palette.focusPlatter else palette.hairline, shape)
            .padding(horizontal = tvDp(24f), vertical = tvDp(14f)),
    ) {
        Text(
            text = label,
            style = CouchType.style(CouchType.caption2, FontWeight.Medium, palette.textFaint),
        )
        Spacer(Modifier.height(tvDp(4f)))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value.ifEmpty { " " },
                style = CouchType.style(CouchType.callout, FontWeight.Medium, palette.text),
                fontFamily = if (secret) FontFamily.Monospace else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (focused) {
                Text(
                    text = "|",
                    style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.accent),
                )
            }
        }
    }
}

// ---- Parental PIN ------------------------------------------------------------------------------------

@Composable
fun PinEntryScreen(
    title: String,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val palette = LocalPalette.current
    var pin by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        UiKeys.handler = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                val char = event.unicodeChar.toChar()
                when {
                    event.keyCode == KeyEvent.KEYCODE_DEL -> {
                        pin = pin.dropLast(1); true
                    }

                    event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER -> {
                        if (pin.length >= 4) onSubmit(pin); true
                    }

                    char.isDigit() && pin.length < 8 -> {
                        pin += char
                        true
                    }

                    else -> false
                }
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Box(Modifier.fillMaxSize().background(backdropBrush(palette)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .panel(palette, RoundedCornerShape(CouchShapes.panel))
                .padding(tvDp(44f)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = CouchType.style(CouchType.title3, FontWeight.Bold, palette.text))
            Spacer(Modifier.height(tvDp(18f)))
            Text(
                text = if (pin.isEmpty()) stringResource(R.string.pin_title) else "*".repeat(pin.length),
                style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.accent),
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(tvDp(24f)))
            Row(horizontalArrangement = Arrangement.spacedBy(tvDp(16f))) {
                PillButton(label = stringResource(R.string.cancel), focused = false, onClick = onCancel)
                PillButton(label = stringResource(R.string.ok), focused = true, onClick = { if (pin.length >= 4) onSubmit(pin) })
            }
        }
    }
}

@Composable
private fun Panel(title: String, content: @Composable () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .width(tvDp(760f))
            .panel(palette, RoundedCornerShape(CouchShapes.panel))
            .padding(tvDp(28f)),
    ) {
        Text(
            text = title,
            style = CouchType.style(CouchType.caption2, FontWeight.Bold, palette.textFaint),
        )
        Spacer(Modifier.height(tvDp(14f)))
        content()
    }
}
