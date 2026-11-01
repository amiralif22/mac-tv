package dev.couchtv.remote.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Three states, one screen each: find a TV, prove you may drive it, drive it.
 *
 * The router is a `Crossfade` rather than a navigation graph: there are three destinations, they all
 * live in one process, and a graph would add a back stack nobody wants (Back should not walk you
 * backwards through a remote).
 */
@Composable
fun RemoteRoot(model: RemoteViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val devices by model.devices.collectAsStateWithLifecycle()
    val searching by model.searching.collectAsStateWithLifecycle()

    val destination = when {
        state.connected -> Destination.Remote
        state.pairingRequired -> Destination.Pairing
        else -> Destination.Devices
    }

    Box(Modifier.fillMaxSize().background(Couch.Canvas)) {
        Crossfade(targetState = destination, animationSpec = tween(260), label = "remote-root") { current ->
            when (current) {
                Destination.Devices -> DevicesScreen(
                    devices = devices,
                    searching = searching,
                    message = state.message,
                    onRefresh = model::refreshDiscovery,
                    onAddAddress = model::addManualAddress,
                    onConnect = model::connect,
                )

                Destination.Pairing -> PairingScreen(
                    tvName = state.tv?.name ?: "your TV",
                    attempts = state.pairingAttempts,
                    message = state.message,
                    onSubmit = model::submitPairingCode,
                    onCancel = model::forgetTv,
                )

                Destination.Remote -> RemoteScreen(state = state, model = model)
            }
        }
    }
}

private enum class Destination { Devices, Pairing, Remote }
