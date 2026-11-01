package dev.couchtv.remote.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.couchtv.remote.R
import dev.couchtv.remote.net.Protocol

/**
 * The TV's home-screen apps, as shortcuts.
 *
 * Icons come over plain HTTP from the TV's own port (`/icon?pkg=…`), which is why this sheet needs
 * no image library: a 96 px PNG is a `BitmapFactory.decodeStream` away, and anything that fails to
 * load falls back to the app's initials on its brand colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsSheet(
    apps: List<Protocol.TvApp>,
    iconUrl: (String) -> String?,
    onLaunch: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Couch.Panel,
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.on_your_tv), style = Couch.title(18))
            Spacer(Modifier.height(12.dp))

            if (apps.isEmpty()) {
                Text(stringResource(R.string.no_apps_yet), style = Couch.Mono, color = Couch.Dim)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(84.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(apps.sortedWith(compareBy({ !it.favourite }, { it.name.lowercase() })), key = { it.packageName }) { app ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.Transparent)
                        ) {
                            AppBadge(app = app, url = iconUrl(app.packageName), onClick = { onLaunch(app.packageName) })
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = app.name,
                                style = Couch.Mono.copy(fontSize = 11.sp),
                                color = Couch.Dim,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBadge(app: Protocol.TvApp, url: String?, onClick: () -> Unit) {
    // The brand colour is the placeholder and the failure state at once: no spinners in a grid of
    // shortcuts, and a 404 can never leave a hole.
    val tint = remember(app.color) { parseColor(app.color) }
    val icon by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            url?.let { address ->
                runCatching {
                    java.net.URL(address).openStream().use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    }
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp)),
            )
        } else {
            Text(
                text = app.name.take(2).uppercase(),
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** "#E50914" → Color. Falls back to the neutral grey so a bad value cannot crash a grid. */
private fun parseColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    if (cleaned.length != 6) return Couch.Faint
    val value = cleaned.toLongOrNull(16) ?: return Couch.Faint
    return Color(0xFF000000 or value)
}

/**
 * A keyboard sheet that types into the TV's focused field.
 *
 * Only the difference is sent: an appended run of characters goes as one `text` frame, deletions as
 * `backspace`. An edit in the middle clears and retypes, which is predictable rather than clever.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(
    onSend: (String) -> Unit,
    onBackspace: (Int) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var buffer by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Couch.Panel) {
        Column(Modifier.padding(18.dp).padding(bottom = 16.dp)) {
            Text(stringResource(R.string.type_on_tv), style = Couch.title(18))
            Spacer(Modifier.height(12.dp))

            BasicTextField(
                value = buffer,
                onValueChange = { next ->
                    val previous = buffer
                    buffer = next
                    when {
                        next.startsWith(previous) && next.length > previous.length ->
                            onSend(next.substring(previous.length))
                        previous.startsWith(next) && previous.length > next.length ->
                            onBackspace(previous.length - next.length)
                        next.isNotEmpty() -> {
                            onBackspace(previous.length)
                            onSend(next)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(14.dp)
                    .focusRequester(focus),
                textStyle = Couch.Mono.copy(color = Couch.Ink, fontSize = 15.sp),
                cursorBrush = SolidColor(Couch.Phosphor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                    autoCorrect = false,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(label = stringResource(R.string.send), prominent = true, onClick = onSubmit)
                PillButton(
                    label = "Delete",
                    onClick = {
                        onBackspace(1)
                        buffer = buffer.dropLast(1)
                    },
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Sent to the TV as you type",
                    style = Couch.Mono,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
