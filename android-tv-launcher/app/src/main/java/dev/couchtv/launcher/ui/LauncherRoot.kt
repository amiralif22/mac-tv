package dev.couchtv.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.couchtv.launcher.ui.CursorPointer
import dev.couchtv.launcher.ui.screens.HomeScreen
import dev.couchtv.launcher.ui.screens.ManageAppsScreen
import dev.couchtv.launcher.ui.screens.OnboardingScreen
import dev.couchtv.launcher.ui.screens.PairingScreen
import dev.couchtv.launcher.ui.screens.PinEntryScreen
import dev.couchtv.launcher.ui.screens.PromptPanel
import dev.couchtv.launcher.ui.screens.QuickSettingsOverlay
import dev.couchtv.launcher.ui.screens.ScreensaverOverlay
import dev.couchtv.launcher.ui.screens.SearchScreen
import dev.couchtv.launcher.ui.screens.SettingsScreen
import dev.couchtv.launcher.ui.screens.SideloadScreen
import dev.couchtv.launcher.ui.screens.ToastOverlay
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.LocalPalette
import kotlin.math.roundToInt
import dev.couchtv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * The whole app: one screen at a time, then whatever is floating above it.
 *
 * Ordering matters. The screen saver covers everything; prompts cover the saver; the cursor covers all
 * of it, because a pointer that disappears behind a panel is worse than no pointer at all.
 */
@Composable
fun LauncherRoot(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val registry = remember { CursorRegistry() }

    DisposableEffect(state.pointerMode) {
        vm.attachCursorRegistry(if (state.pointerMode) registry else null)
        onDispose { vm.attachCursorRegistry(null) }
    }

    CompositionLocalProvider(
        LocalPointerMode provides state.pointerMode,
        LocalCursorRegistry provides registry,
    ) {
        Box(Modifier.fillMaxSize().background(palette.canvas)) {
            when {
                state.onboarding -> OnboardingScreen(vm, state)
                state.screen == Screen.HOME -> HomeScreen(vm, state)
                state.screen == Screen.SETTINGS -> SettingsScreen(vm, state)
                state.screen == Screen.MANAGE_APPS -> ManageAppsScreen(vm, state)
                state.screen == Screen.SEARCH -> SearchScreen(vm, state)
                state.screen == Screen.SIDELOAD -> SideloadScreen(vm, state)
                state.screen == Screen.PAIRING -> PairingScreen(vm, state)
            }

            if (state.overlay == Overlay.QUICK_SETTINGS) {
                QuickSettingsOverlay(vm, state) { vm.toggleQuickSettings() }
            }

            // "Add [App] to your home screen?" — asked once after every install.
            state.addPrompt?.let { prompt ->
                PromptPanel(
                    title = stringResource(R.string.add_after_install, prompt.entry.label),
                    body = stringResource(R.string.add_after_install_body),
                    primary = stringResource(R.string.add),
                    secondary = stringResource(R.string.not_now),
                    onPrimary = { vm.confirmAddPrompt(); vm.openScreen(Screen.HOME) },
                    onSecondary = { vm.dismissAddPrompt() },
                )
            }

            // "This build has no TV entry — keep it, or install the Android TV version?"
            state.compatPrompt?.let { prompt ->
                PromptPanel(
                    title = stringResource(R.string.compat_title_named, prompt.apk.label),
                    body = stringResource(R.string.compat_body),
                    primary = stringResource(R.string.compat_keep),
                    secondary = stringResource(R.string.compat_install_tv),
                    onPrimary = { vm.dismissCompatPrompt() },
                    onSecondary = {
                        vm.dismissCompatPrompt()
                        vm.openSideload()
                    },
                )
            }

            state.serviceMissing?.let { serviceId ->
                val name = state.settings.nuvioAccount
                    ?.let { stringResource(R.string.settings_nuvio) }
                    ?: stringResource(R.string.the_service)
                PromptPanel(
                    title = stringResource(R.string.service_missing_title, name),
                    body = stringResource(R.string.service_missing_body),
                    primary = stringResource(R.string.install),
                    secondary = stringResource(R.string.sideload_action),
                    onPrimary = { vm.installServiceApp(serviceId) },
                    onSecondary = {
                        vm.dismissServiceMissing()
                        vm.openSideload()
                    },
                )
            }

            state.parentalRequest?.let { request ->
                PinEntryScreen(
                    title = stringResource(R.string.pin_locked_app, request.app.label),
                    onCancel = vm::cancelPin,
                    onSubmit = vm::submitPin,
                )
            }

            if (state.saverActive) {
                ScreensaverOverlay(state)
            }

            if (state.toast != null) {
                ToastOverlay(state.toast) { vm.clearToast() }
            }

            if (state.pointerMode && state.cursorVisible) {
                val half = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }
                Box(
                    Modifier.offset {
                        IntOffset(
                            (state.cursorX - half).roundToInt(),
                            (state.cursorY - half).roundToInt(),
                        )
                    }
                ) {
                    CursorPointer(
                        position = Offset(state.cursorX, state.cursorY),
                        hovering = registry.targetAt(Offset(state.cursorX, state.cursorY)) != null,
                        visible = true,
                    )
                }
            }
        }
    }
}
