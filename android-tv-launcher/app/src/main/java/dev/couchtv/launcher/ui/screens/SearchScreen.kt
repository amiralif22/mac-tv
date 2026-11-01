package dev.couchtv.launcher.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.couchtv.launcher.ui.Direction
import dev.couchtv.launcher.ui.FocusEngine
import dev.couchtv.launcher.ui.FocusPos
import dev.couchtv.launcher.ui.LauncherViewModel
import dev.couchtv.launcher.ui.UiKeys
import dev.couchtv.launcher.ui.components.AppTile
import dev.couchtv.launcher.ui.components.MediaPoster
import dev.couchtv.launcher.ui.components.PillButton
import dev.couchtv.launcher.ui.components.RowHeader
import dev.couchtv.launcher.ui.theme.tvDp
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.CouchType
import dev.couchtv.launcher.ui.theme.LocalPalette
import dev.couchtv.launcher.ui.theme.backdropBrush
import dev.couchtv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * One search field across the apps the user chose and everything the streaming adapters cached.
 *
 * Typing comes from whatever keyboard is available: a hardware keyboard, the phone remote's text
 * channel, or the system IME on a tablet. Results are laid out as two rows (apps, then titles) so the
 * D-pad has somewhere to go without learning a new grammar.
 */
@Composable
fun SearchScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val apps = state.searchApps
    val media = state.searchMedia
    var focus by remember { mutableStateOf(FocusPos(0, 0)) }
    val sizes = listOf(1, apps.size, media.size)
    val appRow = rememberLazyListState()
    val mediaRow = rememberLazyListState()

    LaunchedEffect(focus) {
        if (focus.row == 1) appRow.animateScrollToItem(FocusEngine.windowFirst(focus.col, 6, apps.size))
        if (focus.row == 2) mediaRow.animateScrollToItem(FocusEngine.windowFirst(focus.col, 6, media.size))
    }

    // Letters typed on a keyboard land in the query; arrows move between results.
    DisposableEffect(Unit) {
        UiKeys.handler = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { focus = FocusEngine.clamp(FocusEngine.move(focus, Direction.LEFT, sizes), sizes); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { focus = FocusEngine.clamp(FocusEngine.move(focus, Direction.RIGHT, sizes), sizes); true }
                    KeyEvent.KEYCODE_DPAD_UP -> { focus = FocusEngine.move(focus, Direction.UP, sizes); true }
                    KeyEvent.KEYCODE_DPAD_DOWN -> { focus = FocusEngine.move(focus, Direction.DOWN, sizes); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        activate(vm, focus, apps, media)
                        true
                    }
                    KeyEvent.KEYCODE_DEL -> { vm.setQuery(state.query.dropLast(1)); true }
                    else -> {
                        val char = event.unicodeChar.toChar()
                        if (!char.isISOControl()) {
                            vm.setQuery(state.query + char)
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        Column(Modifier.fillMaxSize().padding(horizontal = tvDp(96f), vertical = tvDp(56f))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.search), style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.text))
                Spacer(Modifier.weight(1f))
                PillButton(label = stringResource(R.string.back), focused = false, onClick = { vm.back() })
            }
            Spacer(Modifier.height(tvDp(18f)))

            // The field: a caret and the query, big enough to read from a sofa.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CouchShapes.pill))
                    .background(palette.glass)
                    .border(tvDp(2f), palette.hairline, RoundedCornerShape(CouchShapes.pill))
                    .padding(horizontal = tvDp(36f), vertical = tvDp(20f)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.query.ifEmpty { stringResource(R.string.search_hint) },
                    style = CouchType.style(
                        CouchType.callout,
                        FontWeight.Medium,
                        if (state.query.isEmpty()) palette.textFaint else palette.text,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(tvDp(8f)))
                Text("|", style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.accent))
            }

            Spacer(Modifier.height(tvDp(40f)))

            if (apps.isEmpty() && media.isEmpty()) {
                Text(
                    text = if (state.query.isEmpty()) {
                        stringResource(R.string.search_hint_body)
                    } else {
                        "Nothing matched “${state.query}”."
                    },
                    style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
                )
            }

            if (apps.isNotEmpty()) {
                RowHeader(stringResource(R.string.search_apps))
                LazyRow(
                    state = appRow,
                    horizontalArrangement = Arrangement.spacedBy(tvDp(28f)),
                ) {
                    items(apps.size) { index ->
                        val entry = apps[index]
                        AppTile(
                            entry = entry,
                            focused = focus.row == 1 && focus.col == index,
                            showLabel = true,
                            pinned = entry.packageName in state.pins,
                            icon = vm.icon(entry.packageName),
                            onClick = {
                                focus = FocusPos(1, index)
                                vm.launchApp(entry)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(tvDp(28f)))
            }

            if (media.isNotEmpty()) {
                RowHeader(stringResource(R.string.search_media))
                LazyRow(
                    state = mediaRow,
                    horizontalArrangement = Arrangement.spacedBy(tvDp(28f)),
                ) {
                    items(media.size) { index ->
                        val item = media[index]
                        MediaPoster(
                            item = item,
                            focused = focus.row == 2 && focus.col == index,
                            onClick = {
                                focus = FocusPos(2, index)
                                vm.openMedia(item)
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun activate(
    vm: LauncherViewModel,
    focus: FocusPos,
    apps: List<dev.couchtv.launcher.data.AppEntry>,
    media: List<dev.couchtv.launcher.streaming.MediaItem>,
) {
    when (focus.row) {
        1 -> apps.getOrNull(focus.col)?.let(vm::launchApp)
        2 -> media.getOrNull(focus.col)?.let(vm::openMedia)
        else -> Unit
    }
}
