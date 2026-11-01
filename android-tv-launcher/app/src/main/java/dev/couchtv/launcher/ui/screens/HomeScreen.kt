package dev.couchtv.launcher.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.couchtv.launcher.data.NotificationItem
import dev.couchtv.launcher.streaming.MediaItem
import dev.couchtv.launcher.ui.FocusEngine
import dev.couchtv.launcher.ui.HomeItem
import dev.couchtv.launcher.ui.HomeRow
import dev.couchtv.launcher.ui.LauncherViewModel
import dev.couchtv.launcher.ui.RowKind
import dev.couchtv.launcher.ui.components.AppTile
import dev.couchtv.launcher.ui.components.MediaPoster
import dev.couchtv.launcher.ui.components.MetaChip
import dev.couchtv.launcher.ui.components.PillButton
import dev.couchtv.launcher.ui.components.RowHeader
import dev.couchtv.launcher.ui.cursorTarget
import dev.couchtv.launcher.ui.components.glass
import dev.couchtv.launcher.ui.theme.tvDp
import dev.couchtv.launcher.ui.theme.CouchPalette
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.CouchType
import dev.couchtv.launcher.ui.theme.LocalDeviceProfile
import dev.couchtv.launcher.ui.theme.LocalPalette
import dev.couchtv.launcher.ui.theme.Motion
import dev.couchtv.launcher.ui.theme.backdropBrush
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.couchtv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * The home screen: the header, the status row, and the rows of content — in the order the brief
 * fixes, drawn in the Mac app's palette.
 *
 * Focus is rendered, never owned: the view model holds one [dev.couchtv.launcher.ui.FocusPos] and
 * every card asks whether it is the focused one. That is why the D-pad, a game controller, a
 * Bluetooth remote and the phone remote all behave identically, and why the row that scrolls is
 * always the row that has focus.
 */
@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    state: LauncherViewModel.UiState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalPalette.current
    val contentRows = remember(state.rows) {
        state.rows.withIndex().filter { it.value.kind != RowKind.STATUS }
    }
    val listState = rememberLazyListState()

    // Keep the focused row on screen: the row under the D-pad is never half off the bottom.
    LaunchedEffect(state.focus.row, contentRows.size) {
        val target = contentRows.indexOfFirst { it.index == state.focus.row }
        if (target >= 0) listState.animateScrollToItem(target)
    }

    Box(modifier.fillMaxSize().background(backdropBrush(palette))) {
        ContentBackdrop(vm, state)

        Column(Modifier.fillMaxSize()) {
            HomeHeader(vm, state)
            NewAppBanner(state)

            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = stringResource(R.string.loading_apps),
                        style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.textDim),
                        modifier = Modifier.padding(start = tvDp(96f), top = tvDp(40f)),
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = tvDp(32f), bottom = tvDp(80f)),
                verticalArrangement = Arrangement.spacedBy(tvDp(52f)),
            ) {
                itemsIndexed(contentRows, key = { _, entry -> entry.value.id }) { _, entry ->
                    HomeRowSection(vm, state, entry.index, entry.value)
                }
            }
        }
    }
}

/**
 * The wallpaper: the focused app's or title's colour, with its artwork blurred behind.
 * It changes as focus moves, slowly and quietly — Apple TV's backdrop, not a slideshow.
 */
@Composable
private fun ContentBackdrop(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    val item = state.rows.getOrNull(state.focus.row)?.items?.getOrNull(state.focus.col)

    val accent = when (item) {
        is HomeItem.App -> Color(item.entry.accent)
        is HomeItem.Media -> Color(
            item.item.accent?.let { hex ->
                runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(palette.accentDeep)
            } ?: palette.accentDeep
        )

        else -> palette.accentDeep
    }
    val animatedAccent by animateColorAsState(accent, tween(Motion.appearMs), label = "accent")
    val poster = (item as? HomeItem.Media)?.item?.backdropUrl
        ?: (item as? HomeItem.Media)?.item?.posterUrl

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            animatedAccent.copy(alpha = 0.42f).compositeOver(palette.canvas),
                            palette.canvas,
                        ),
                        center = Offset(1400f, 260f),
                        radius = 2100f,
                    )
                )
        )

        if (poster != null) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(poster)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(tvDp(120f))
            )
        }

        // Legibility: darken the left where text sits, and the bottom under the rows.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(palette.canvas.copy(alpha = 0.92f), palette.canvas.copy(alpha = 0.45f), Color.Transparent)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, palette.canvas.copy(alpha = 0.55f), palette.canvas.copy(alpha = 0.92f))
                    )
                )
        )
    }
}

/** Clock, date, network, and the status row's pills — the Mac app's top-right cluster. */
@Composable
private fun HomeHeader(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(20_000)
            now = Date()
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = tvDp(96f), end = tvDp(96f), top = tvDp(48f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = time,
                style = CouchType.style(CouchType.title3, FontWeight.Bold, palette.text),
            )
            Text(
                text = date,
                style = CouchType.style(CouchType.caption2, FontWeight.Medium, palette.textDim),
            )
        }

        Spacer(Modifier.width(tvDp(28f)))
        MetaChip(state.network.label)
        if (state.streaming.offline) {
            Spacer(Modifier.width(tvDp(14f)))
            MetaChip(stringResource(R.string.offline_short), accent = palette.warning)
        }
        if (state.remoteClients > 0) {
            Spacer(Modifier.width(tvDp(14f)))
            MetaChip("${state.remoteClients} phone${if (state.remoteClients == 1) "" else "s"}", accent = palette.accent)
        }

        Spacer(Modifier.weight(1f))

        // The status row: the same pills a D-pad reaches by pressing up.
        val statusRow = state.rows.firstOrNull { it.kind == RowKind.STATUS }
        Row(horizontalArrangement = Arrangement.spacedBy(tvDp(14f)), verticalAlignment = Alignment.CenterVertically) {
            statusRow?.items?.forEachIndexed { index, item ->
                val focused = state.focus.row == 0 && state.focus.col == index
                PillButton(
                    label = item.title,
                    focused = focused,
                    modifier = Modifier.cursorTarget(
                        id = "status:$index",
                        onFocus = { vm.focusOn(0, index) },
                        onActivate = { vm.focusOn(0, index); vm.activateFocus() },
                    ),
                    onClick = { vm.focusOn(0, index); vm.activateFocus() },
                )
            }
        }
    }
}

/** "New app detected: [App]. Add to home screen?" — quiet, dismissible, above everything. */
@Composable
private fun NewAppBanner(state: LauncherViewModel.UiState) {
    val entry = state.newApps.firstOrNull() ?: return
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .padding(start = tvDp(96f), top = tvDp(24f))
            .glass(palette, RoundedCornerShape(CouchShapes.pill))
            .padding(horizontal = tvDp(32f), vertical = tvDp(16f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tvDp(16f)),
    ) {
        Text(
            text = "New app detected: ${entry.label}. Add to home screen?",
            style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.text),
        )
        Text(
            text = stringResource(R.string.home_add_hint),
            style = CouchType.style(CouchType.caption2, FontWeight.Medium, palette.textFaint),
        )
    }
}

/** One row: a title, then its items. */
@Composable
private fun HomeRowSection(
    vm: LauncherViewModel,
    state: LauncherViewModel.UiState,
    rowIndex: Int,
    row: HomeRow,
) {
    val profile = LocalDeviceProfile.current
    val rowState = rememberLazyListState()
    val focusedCol = if (state.focus.row == rowIndex) state.focus.col else -1
    val itemWidth = when (row.kind) {
        RowKind.APPS, RowKind.FAVORITES -> 125f
        RowKind.NOTIFICATIONS -> 210f
        else -> 100f
    }
    val visible = ((profile.canvasWidthDp - 96f) / (itemWidth + 14f)).toInt().coerceAtLeast(1)

    LaunchedEffect(state.focus.row, state.focus.col) {
        if (state.focus.row == rowIndex) {
            val first = FocusEngine.windowFirst(state.focus.col, visible, row.items.size)
            rowState.animateScrollToItem(first)
        }
    }

    Column {
        if (row.title != null) {
            RowHeader(
                text = row.title,
                modifier = Modifier.padding(horizontal = tvDp(96f)),
            )
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = tvDp(96f), vertical = tvDp(8f)),
            horizontalArrangement = Arrangement.spacedBy(tvDp(28f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(row.items, key = { _, item -> item.id }) { index, item ->
                val focused = index == focusedCol
                val target = Modifier.cursorTarget(
                    id = "${row.id}:$index",
                    onFocus = { vm.focusOn(rowIndex, index) },
                    onActivate = { vm.focusOn(rowIndex, index); vm.activateFocus() },
                )
                when (item) {
                    is HomeItem.App -> AppTile(
                        entry = item.entry,
                        focused = focused,
                        showLabel = focused || profile.alwaysShowLabels,
                        pinned = item.entry.packageName in state.pins,
                        icon = vm.icon(item.entry.packageName),
                        modifier = target,
                        onLongClick = { vm.togglePin(item.entry.packageName) },
                        onClick = { vm.focusOn(rowIndex, index); vm.activateFocus() },
                    )

                    is HomeItem.Media -> MediaPoster(
                        item = item.item,
                        focused = focused,
                        modifier = target,
                        onLongClick = { vm.togglePin(item.item.id) },
                        onClick = { vm.focusOn(rowIndex, index); vm.activateFocus() },
                    )

                    is HomeItem.Action -> ActionTile(
                        label = item.title,
                        focused = focused,
                        modifier = target,
                        onClick = { vm.focusOn(rowIndex, index); vm.activateFocus() },
                    )

                    is HomeItem.Notice -> NoticeCard(
                        notice = item.item,
                        focused = focused,
                        modifier = target,
                        onClick = { vm.focusOn(rowIndex, index); vm.activateFocus() },
                    )
                }
            }
        }
    }
}

/** The dashed "add apps" tile, matching the Mac app's plus tile. */
@Composable
private fun ActionTile(
    label: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(tvDp(22f))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(tvDp(250f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(tvDp(150f))
                .clip(shape)
                .background(if (focused) palette.focusPlatter else palette.glass)
                .border(
                    width = tvDp(3f),
                    color = if (focused) Color.Transparent else palette.hairline,
                    shape = shape,
                )
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (focused) label else "+",
                style = CouchType.style(
                    if (focused) CouchType.caption1 else CouchType.title3,
                    FontWeight.Medium,
                    if (focused) palette.focusText else palette.text,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A notification from another app: title, app and a line of text. Tapping dismisses it. */
@Composable
private fun NoticeCard(
    notice: NotificationItem,
    focused: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(tvDp(20f))
    Column(
        modifier = modifier
            .width(tvDp(420f))
            .clip(shape)
            .background(if (focused) palette.surfaceRaised else palette.glass)
            .border(tvDp(2f), if (focused) palette.focusPlatter else palette.hairline, shape)
            .padding(tvDp(24f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = notice.appLabel,
                style = CouchType.style(CouchType.caption2, FontWeight.Bold, palette.accent),
            )
        }
        Spacer(Modifier.height(tvDp(8f)))
        Text(
            text = notice.title.ifBlank { notice.text },
            style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.text),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (notice.text.isNotBlank()) {
            Spacer(Modifier.height(tvDp(6f)))
            Text(
                text = notice.text,
                style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.textDim),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
