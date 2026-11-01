package dev.couchtv.remote.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.couchtv.remote.R
import dev.couchtv.remote.net.Protocol
import dev.couchtv.remote.net.RemoteClient

/**
 * The remote proper. Touchpad first, D-pad under the thumb, transport only when something plays, and
 * the system row at the bottom — the same order as a television remote, for the same reason.
 */
@Composable
fun RemoteScreen(state: RemoteUiState, model: RemoteViewModel) {
    var showSettings by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableStateOf(1.0f) }

    // Voice runs inside the system's recognition app: this app never holds the microphone.
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) model.voice(spoken)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Header(state = state, onSettings = { showSettings = true })

        Spacer(Modifier.height(12.dp))

        Touchpad(
            sensitivity = sensitivity,
            onPointer = model::pointer,
            onClick = model::click,
            onLongPress = model::longPress,
            onScroll = model::scroll,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )

        Spacer(Modifier.height(12.dp))
        DPAD(onKey = model::key)
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadKey(icon = Icons.Filled.ArrowBack, label = stringResource(R.string.back), size = 44, onPress = { model.key(Protocol.Key.BACK) })
            PadKey(icon = Icons.Filled.Home, label = stringResource(R.string.home), size = 44, onPress = { model.key(Protocol.Key.HOME) })
            PadKey(icon = Icons.Filled.Keyboard, label = stringResource(R.string.keyboard), size = 44, onPress = { showKeyboard = true })
            PadKey(
                icon = Icons.Filled.Mic,
                label = stringResource(R.string.voice),
                size = 44,
                onPress = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search on TV")
                    }
                    runCatching { voice.launch(intent) }
                        .onFailure { model.key(Protocol.Key.SEARCH) }
                },
            )
            PadKey(icon = Icons.Filled.Apps, label = stringResource(R.string.apps), size = 44, onPress = { showApps = true })
        }

        state.tvState.media?.let { media ->
            Spacer(Modifier.height(10.dp))
            Transport(media = media, onKey = model::key)
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadKey(icon = Icons.Filled.VolumeUp, label = "+", size = 44, onPress = { model.key(Protocol.Key.VOLUME_UP) })
            PadKey(icon = Icons.Filled.VolumeDown, label = "−", size = 44, onPress = { model.key(Protocol.Key.VOLUME_DOWN) })
            PadKey(
                icon = if (state.tvState.muted == true) Icons.Filled.VolumeOff else Icons.Filled.Speaker,
                label = stringResource(R.string.remote),
                size = 44,
                onPress = { model.key(Protocol.Key.MUTE) },
            )
            PadKey(icon = Icons.Filled.DarkMode, label = "Power", size = 44, onPress = { model.key(Protocol.Key.POWER) })
        }
        Spacer(Modifier.height(8.dp))
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = model::clearMessage,
            title = { Text(stringResource(R.string.app_name)) },
            text = { Text(message, color = Couch.Dim) },
            confirmButton = {
                TextButton(onClick = model::clearMessage) { Text("OK", color = Couch.Phosphor) }
            },
            containerColor = Couch.Panel,
        )
    }

    if (showApps) {
        AppsSheet(
            apps = state.apps,
            iconUrl = { pkg -> state.tv?.iconUrl(pkg) },
            onLaunch = { pkg -> model.launch(pkg); showApps = false },
            onDismiss = { showApps = false },
        )
    }

    if (showKeyboard) {
        KeyboardSheet(onSend = model::type, onBackspace = model::backspace, onSubmit = model::submit, onDismiss = { showKeyboard = false })
    }

    if (showSettings) {
        SettingsDialog(
            sensitivity = sensitivity,
            onSensitivity = { sensitivity = it },
            onForget = { showSettings = false; model.forgetTv() },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun Header(state: RemoteUiState, onSettings: () -> Unit) {
    val (label, color) = when (val link = state.link) {
        is RemoteClient.LinkState.Connected -> link.tvName to Couch.Phosphor
        is RemoteClient.LinkState.Reconnecting -> stringResource(R.string.reconnecting) to Couch.Warning
        is RemoteClient.LinkState.Failed -> link.message to Couch.Warning
        is RemoteClient.LinkState.AwaitingPairing -> stringResource(R.string.pairing) to Couch.Warning
        else -> stringResource(R.string.looking_for_tvs) to Couch.Faint
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Couch.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val detail = buildList {
                state.roundTripMs?.let { add("$it ms") }
                state.tvState.screen?.let { add(it) }
                state.tvState.volume?.let { add("${(it * 100).toInt()}%") }
            }
            Text(detail.joinToString(" · ").ifEmpty { "—" }, style = Couch.Mono)
        }
        PadKey(icon = Icons.Filled.Tune, label = stringResource(R.string.cd_settings), size = 38, onPress = onSettings)
    }
}

@Composable
private fun DPAD(onKey: (Protocol.Key) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PadKey(icon = Icons.Filled.KeyboardArrowUp, label = stringResource(R.string.cd_up), size = 48, onPress = { onKey(Protocol.Key.UP) })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PadKey(icon = Icons.Filled.KeyboardArrowLeft, label = stringResource(R.string.cd_left), size = 48, onPress = { onKey(Protocol.Key.LEFT) })
            PadKey(label = stringResource(R.string.ok), size = 72, onPress = { onKey(Protocol.Key.OK) })
            PadKey(icon = Icons.Filled.KeyboardArrowRight, label = stringResource(R.string.cd_right), size = 48, onPress = { onKey(Protocol.Key.RIGHT) })
        }
        PadKey(icon = Icons.Filled.KeyboardArrowDown, label = stringResource(R.string.cd_down), size = 48, onPress = { onKey(Protocol.Key.DOWN) })
    }
}

@Composable
private fun Transport(media: Protocol.TvState.Media, onKey: (Protocol.Key) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Couch.Glass)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(media.title, color = Couch.Ink, fontWeight = FontWeight.SemiBold, maxLines = 1)
            media.subtitle?.let { Text(it, style = Couch.Mono, maxLines = 1) }
        }
        PadKey(icon = Icons.Filled.SkipPrevious, label = "Previous", size = 40, onPress = { onKey(Protocol.Key.PREV) })
        Spacer(Modifier.width(6.dp))
        PadKey(
            icon = if (media.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            label = "Play or pause",
            size = 44,
            onPress = { onKey(Protocol.Key.PLAY_PAUSE) },
        )
        Spacer(Modifier.width(6.dp))
        PadKey(icon = Icons.Filled.SkipNext, label = "Next", size = 40, onPress = { onKey(Protocol.Key.NEXT) })
    }
}

@Composable
private fun SettingsDialog(
    sensitivity: Float,
    onSensitivity: (Float) -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Couch.Panel,
        title = { Text(stringResource(R.string.remote), color = Couch.Ink) },
        text = {
            Column {
                Text(stringResource(R.string.pointer_speed), style = Couch.Mono, color = Couch.Dim)
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivity,
                    valueRange = 0.4f..2.5f,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = Couch.Phosphor) }
        },
        dismissButton = {
            TextButton(onClick = onForget) {
                Text(stringResource(R.string.forget_tv), color = Couch.Warning)
            }
        },
    )
}
