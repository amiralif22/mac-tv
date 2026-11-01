package dev.couchtv.remote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.couchtv.remote.R
import dev.couchtv.remote.net.DiscoveredTv

/**
 * Find a TV, or type its address.
 *
 * The manual field is always visible. A list that says "no TVs yet" and offers nothing else is the
 * most common complaint about companion remotes, and the fix costs one text field.
 */
@Composable
fun DevicesScreen(
    devices: List<DiscoveredTv>,
    searching: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onAddAddress: (String) -> DiscoveredTv?,
    onConnect: (DiscoveredTv) -> Unit,
) {
    val context = LocalContext.current
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(top = 32.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (searching) Couch.Phosphor else Couch.Faint)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(if (searching) R.string.looking_for_tvs else R.string.discovery_stopped),
                        style = Couch.Label,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.search_again),
                            tint = Couch.Dim,
                        )
                    }
                }
                Text(stringResource(R.string.app_name), style = Couch.title(30))
                Text(
                    text = stringResource(R.string.choose_tv),
                    style = Couch.Mono,
                    color = Couch.Dim,
                )
            }
        }

        if (devices.isEmpty()) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Couch.Glass)
                        .border(1.dp, Couch.Hairline, RoundedCornerShape(18.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.no_tvs_title),
                        color = Couch.Ink,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.no_tvs_body),
                        style = Couch.Mono,
                        color = Couch.Dim,
                    )
                }
            }
        }

        items(devices, key = { it.id }) { tv ->
            TvRow(tv = tv, onClick = { onConnect(tv) })
        }

        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.add_by_address), style = Couch.Label)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = Couch.Mono.copy(color = Couch.Ink),
                        placeholder = { Text("192.168.1.42:8765", style = Couch.Mono) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Couch.Phosphor,
                            unfocusedBorderColor = Couch.Hairline,
                        ),
                    )
                    Spacer(Modifier.width(10.dp))
                    PillButton(
                        label = stringResource(R.string.add),
                        onClick = {
                            val tv = onAddAddress(address)
                            if (tv != null) {
                                address = ""
                                error = null
                                onConnect(tv)
                            } else {
                                error = context.getString(R.string.not_an_address)
                            }
                        },
                    )
                }
                (error ?: message)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Couch.Warning, style = Couch.Mono)
                }
            }
        }

        item {
            Text(
                text = "Both devices must be on the same Wi-Fi. The remote never leaves your network.",
                style = Couch.Mono,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun TvRow(tv: DiscoveredTv, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Couch.Glass)
            .border(1.dp, Couch.Hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 46.dp, height = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Tv,
                contentDescription = null,
                tint = Couch.Dim,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tv.name, color = Couch.Ink, fontWeight = FontWeight.SemiBold)
            Text("${tv.model} · ${tv.address}", style = Couch.Mono)
        }
        if (tv.pairing) {
            Text(
                text = stringResource(R.string.pairing),
                style = Couch.Label,
                color = Couch.Phosphor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Couch.Phosphor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
