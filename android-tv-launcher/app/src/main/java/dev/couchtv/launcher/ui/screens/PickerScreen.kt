package dev.couchtv.launcher.ui.screens

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.couchtv.launcher.data.AppCategory
import dev.couchtv.launcher.data.AppEntry
import dev.couchtv.launcher.data.TvCompat
import dev.couchtv.launcher.data.needsRemoteWarning
import dev.couchtv.launcher.ui.Direction
import dev.couchtv.launcher.ui.FocusEngine
import dev.couchtv.launcher.ui.FocusPos
import dev.couchtv.launcher.ui.LauncherViewModel
import dev.couchtv.launcher.ui.UiKeys
import dev.couchtv.launcher.ui.components.CompatBadge
import dev.couchtv.launcher.ui.components.PillButton
import dev.couchtv.launcher.ui.cursorTarget
import dev.couchtv.launcher.ui.components.focusScale
import dev.couchtv.launcher.ui.theme.tvDp
import dev.couchtv.launcher.ui.theme.CouchPalette
import dev.couchtv.launcher.ui.theme.CouchShapes
import dev.couchtv.launcher.ui.theme.CouchType
import dev.couchtv.launcher.ui.theme.LocalDeviceProfile
import dev.couchtv.launcher.ui.theme.LocalPalette
import dev.couchtv.launcher.ui.theme.backdropBrush
import dev.couchtv.launcher.R
import androidx.compose.ui.res.stringResource

/**
 * The first thing a new install shows: **Choose your apps**.
 *
 * Every app on the device is listed, grouped by category, with nothing selected by default. Only what
 * the user ticks ever reaches the home screen — the brief's hardest rule. The same composable is the
 * Manage Apps screen later, so there is exactly one implementation of "what is on my home screen".
 *
 * It is a real 2D grid for the D-pad: category headers are rows of zero focusable width, so up and
 * down skip them, and left and right never leave the row.
 */

private enum class PickerRowKind { CONTROLS, HEADER, APPS, FOOTER }

private data class PickerRow(
    val key: String,
    val kind: PickerRowKind,
    val title: String? = null,
    val apps: List<AppEntry> = emptyList(),
)

@Composable
fun OnboardingScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        AppPicker(
            vm = vm,
            apps = state.apps,
            selected = state.selected,
            title = stringResource(R.string.onboarding_title),
            subtitle = stringResource(R.string.onboarding_subtitle),
            onToggle = vm::toggleSelected,
            onSelectAll = vm::selectAll,
            onDeselectAll = vm::deselectAll,
            footer = { focused ->
                PillButton(
                    label = stringResource(R.string.onboarding_continue),
                    focused = focused,
                    prominent = true,
                    onClick = vm::finishOnboarding,
                )
            },
            onFinish = vm::finishOnboarding,
            lockToggle = null,
        )
    }
}

@Composable
fun ManageAppsScreen(vm: LauncherViewModel, state: LauncherViewModel.UiState) {
    val palette = LocalPalette.current
    Box(Modifier.fillMaxSize().background(backdropBrush(palette))) {
        AppPicker(
            vm = vm,
            apps = state.apps,
            selected = state.selected,
            title = stringResource(R.string.settings_apps),
            subtitle = stringResource(R.string.picker_manage_hint),
            onToggle = vm::toggleSelected,
            onSelectAll = vm::selectAll,
            onDeselectAll = vm::deselectAll,
            footer = { focused ->
                PillButton(
                    label = stringResource(R.string.done),
                    focused = focused,
                    prominent = true,
                    onClick = { vm.openScreen(dev.couchtv.launcher.ui.Screen.HOME) },
                )
            },
            onFinish = { vm.openScreen(dev.couchtv.launcher.ui.Screen.HOME) },
            lockToggle = vm::toggleLocked,
        )
    }
}

@Composable
private fun AppPicker(
    vm: LauncherViewModel,
    apps: List<AppEntry>,
    selected: List<String>,
    title: String,
    subtitle: String,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onFinish: () -> Unit,
    lockToggle: ((String) -> Unit)?,
    footer: @Composable (Boolean) -> Unit,
) {
    val palette = LocalPalette.current
    val profile = LocalDeviceProfile.current
    val columns = when {
        profile.kind == dev.couchtv.launcher.util.DeviceProfile.Kind.LEANBACK -> 6
        profile.canvasWidthDp >= 900f -> 5
        profile.canvasWidthDp >= 600f -> 4
        else -> 3
    }
    val selectedSet = remember(selected) { selected.toSet() }
    val rows = remember(apps, columns) { pickerRows(apps, columns) }
    val focusSizes = remember(rows) {
        rows.map {
            when (it.kind) {
                PickerRowKind.CONTROLS -> 2
                PickerRowKind.HEADER -> 0
                PickerRowKind.APPS -> it.apps.size
                PickerRowKind.FOOTER -> 1
            }
        }
    }

    var focus by remember { mutableStateOf(FocusPos(0, 0)) }
    val listState = rememberLazyListState()

    LaunchedEffect(focus.row) { listState.animateScrollToItem(focus.row.coerceAtLeast(0)) }

    // D-pad and hardware keys belong to the grid while it is on screen.
    DisposableEffect(rows) {
        UiKeys.handler = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) false
            else when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { focus = FocusEngine.move(focus, Direction.LEFT, focusSizes); true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { focus = FocusEngine.move(focus, Direction.RIGHT, focusSizes); true }
                KeyEvent.KEYCODE_DPAD_UP -> { focus = FocusEngine.move(focus, Direction.UP, focusSizes); true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { focus = FocusEngine.move(focus, Direction.DOWN, focusSizes); true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    activate(rows, focus, onToggle, onSelectAll, onDeselectAll, onFinish)
                    true
                }
                else -> false
            }
        }
        onDispose { UiKeys.handler = null }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = tvDp(96f), end = tvDp(96f), top = tvDp(64f))) {
            Text(title, style = CouchType.style(CouchType.title2, FontWeight.Bold, palette.text))
            Spacer(Modifier.height(tvDp(10f)))
            Text(
                subtitle,
                style = CouchType.style(CouchType.caption1, FontWeight.Normal, palette.textDim),
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = tvDp(96f), end = tvDp(96f), top = tvDp(36f), bottom = tvDp(60f),
            ),
            verticalArrangement = Arrangement.spacedBy(tvDp(20f)),
        ) {
            itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                val isFocusedRow = focus.row == index
                when (row.kind) {
                    PickerRowKind.CONTROLS -> ControlsRow(
                        focusedIndex = if (isFocusedRow) focus.col else -1,
                        count = selectedSet.size,
                        total = apps.size,
                        onFocus = { col -> focus = FocusPos(index, col) },
                        onSelectAll = onSelectAll,
                        onDeselectAll = onDeselectAll,
                    )

                    PickerRowKind.HEADER -> Text(
                        text = row.title.orEmpty(),
                        style = CouchType.style(CouchType.callout, FontWeight.Bold, palette.textDim),
                        modifier = Modifier.padding(top = tvDp(18f)),
                    )

                    PickerRowKind.APPS -> Row(horizontalArrangement = Arrangement.spacedBy(tvDp(20f))) {
                        row.apps.forEachIndexed { cellIndex, app ->
                            val focused = isFocusedRow && focus.col == cellIndex
                            PickerCell(
                                app = app,
                                ticked = app.packageName in selectedSet,
                                focused = focused,
                                locked = vm.isLocked(app.packageName),
                                icon = vm.icon(app.packageName),
                                modifier = Modifier.cursorTarget(
                                    id = "pick:${app.packageName}",
                                    onFocus = { focus = FocusPos(index, cellIndex) },
                                    onActivate = { onToggle(app.packageName) },
                                ),
                                onClick = {
                                    focus = FocusPos(index, cellIndex)
                                    onToggle(app.packageName)
                                },
                                onLongClick = lockToggle?.let { { it(app.packageName) } },
                            )
                        }
                    }

                    PickerRowKind.FOOTER -> Row(horizontalArrangement = Arrangement.spacedBy(tvDp(20f))) {
                        footer(isFocusedRow)
                    }
                }
            }
        }
    }
}

private fun activate(
    rows: List<PickerRow>,
    focus: FocusPos,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onFinish: () -> Unit,
) {
    when (rows.getOrNull(focus.row)?.kind) {
        PickerRowKind.CONTROLS -> if (focus.col == 0) onSelectAll() else onDeselectAll()
        PickerRowKind.APPS -> rows[focus.row].apps.getOrNull(focus.col)?.let { onToggle(it.packageName) }
        PickerRowKind.FOOTER -> onFinish()
        else -> Unit
    }
}

/** Category headers plus chunks of [columns] apps; headers carry zero focusable width. */
private fun pickerRows(apps: List<AppEntry>, columns: Int): List<PickerRow> = buildList {
    add(PickerRow("controls", PickerRowKind.CONTROLS))
    AppCategory.entries.forEach { category ->
        val inCategory = apps.filter { it.category == category }
        if (inCategory.isEmpty()) return@forEach
        add(PickerRow("header:${category.name}", PickerRowKind.HEADER, titleFor(category)))
        inCategory.chunked(columns).forEachIndexed { index, chunk ->
            add(PickerRow("row:${category.name}:$index", PickerRowKind.APPS, apps = chunk))
        }
    }
    add(PickerRow("footer", PickerRowKind.FOOTER))
}

private fun titleFor(category: AppCategory): String = when (category) {
    AppCategory.STREAMING -> stringResource(R.string.category_streaming)
    AppCategory.GAMES -> stringResource(R.string.category_games)
    AppCategory.UTILITIES -> stringResource(R.string.category_utilities)
    AppCategory.SYSTEM -> stringResource(R.string.category_system)
    AppCategory.OTHER -> stringResource(R.string.category_other)
}

@Composable
private fun ControlsRow(
    focusedIndex: Int,
    count: Int,
    total: Int,
    onFocus: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tvDp(16f)),
    ) {
        PillButton(
            label = stringResource(R.string.onboarding_select_all),
            focused = focusedIndex == 0,
            onClick = { onFocus(0); onSelectAll() },
        )
        PillButton(
            label = stringResource(R.string.onboarding_deselect_all),
            focused = focusedIndex == 1,
            onClick = { onFocus(1); onDeselectAll() },
        )
        Spacer(Modifier.width(tvDp(16f)))
        Text(
            text = "$count of $total selected",
            style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.textDim),
        )
    }
}

/**
 * One app in the picker: artwork, a tick, the name, and — if it isn't made for a remote — the same
 * warning badge the home screen shows.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickerCell(
    app: AppEntry,
    ticked: Boolean,
    focused: Boolean,
    locked: Boolean,
    icon: android.graphics.drawable.Drawable?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val palette = LocalPalette.current
    val scale = focusScale(focused)
    val shape = RoundedCornerShape(tvDp(20f))
    val accent = Color(app.accent)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .width(tvDp(230f))
            .clip(shape)
            .background(if (focused) palette.focusPlatter.copy(alpha = 0.14f) else palette.glass)
            .border(
                width = if (focused) tvDp(3f) else tvDp(2f),
                color = if (focused) palette.focusPlatter else palette.hairline,
                shape = shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(tvDp(16f)),
    ) {
        Box(
            Modifier
                .size(tvDp(150f))
                .clip(RoundedCornerShape(tvDp(18f)))
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = 0.5f).compositeOver(palette.surface),
                            accent.copy(alpha = 0.2f).compositeOver(palette.surface),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(icon).crossfade(true).build(),
                    contentDescription = app.label,
                    modifier = Modifier.size(tvDp(80f)),
                )
            } else {
                Text(app.label.take(1), style = CouchType.style(CouchType.title3, FontWeight.Bold, palette.text))
            }

            // The tick: a circle at the corner, filled when chosen.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(tvDp(8f))
                    .size(tvDp(44f))
                    .clip(CircleShape)
                    .background(if (ticked) palette.accent else Color.Black.copy(alpha = 0.45f))
                    .border(tvDp(2f), Color.White.copy(alpha = 0.7f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (ticked) {
                    Text("✓", style = CouchType.style(CouchType.caption1, FontWeight.Bold, Color.Black))
                }
            }

            if (locked) {
                Text(
                    text = "🔒",
                    style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.text),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(tvDp(8f)),
                )
            }

            if (app.compat.needsRemoteWarning()) {
                CompatBadge(
                    compat = app.compat,
                    modifier = Modifier.align(Alignment.BottomStart).padding(tvDp(8f)),
                )
            }
        }

        Spacer(Modifier.height(tvDp(12f)))
        Text(
            text = app.label,
            style = CouchType.style(CouchType.caption1, FontWeight.Medium, palette.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (app.compat == TvCompat.TOUCH_ONLY) {
            Text(
                text = stringResource(R.string.not_tv_friendly_short),
                style = CouchType.style(CouchType.caption2, FontWeight.Normal, palette.warning),
            )
        }
    }
}
