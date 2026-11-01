package dev.couchtv.remote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.couchtv.remote.BuildConfig
import dev.couchtv.remote.net.DeviceIdentity
import dev.couchtv.remote.net.DiscoveredTv
import dev.couchtv.remote.net.Discovery
import dev.couchtv.remote.net.Protocol
import dev.couchtv.remote.net.RemoteClient
import dev.couchtv.remote.net.TokenVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the UI reads, in one immutable snapshot. The screens are pure functions of this, which
 * keeps recomposition predictable and state bugs out of the composables.
 */
data class RemoteUiState(
    val link: RemoteClient.LinkState = RemoteClient.LinkState.Idle,
    val tv: DiscoveredTv? = null,
    val tvState: Protocol.TvState = Protocol.TvState(),
    val apps: List<Protocol.TvApp> = emptyList(),
    val roundTripMs: Int? = null,
    val pairingRequired: Boolean = false,
    val pairingAttempts: Int = 0,
    val message: String? = null,
    val connecting: Boolean = false,
) {
    val connected: Boolean get() = link is RemoteClient.LinkState.Connected
}

/**
 * The one place that talks to [RemoteClient] and [Discovery]. It owns the lifetime of both and
 * exposes a single StateFlow, so the Compose tree never holds a socket.
 */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val vault = TokenVault(context)
    private val discovery = Discovery(context)

    private val client = RemoteClient(
        deviceId = DeviceIdentity.id(context),
        deviceName = DeviceIdentity.name(context),
        vault = vault,
        appVersion = BuildConfig.VERSION_NAME,
        listener = object : RemoteClient.Listener {
            override fun onLinkState(state: RemoteClient.LinkState) {
                _state.value = _state.value.copy(
                    link = state,
                    connecting = state is RemoteClient.LinkState.Connecting ||
                        state is RemoteClient.LinkState.Handshaking ||
                        state is RemoteClient.LinkState.Reconnecting,
                    pairingRequired = state is RemoteClient.LinkState.AwaitingPairing,
                )
            }

            override fun onServerMessage(message: Protocol.ServerMessage) {
                update(message)
            }

            override fun onError(message: String) {
                _state.value = _state.value.copy(message = message)
            }
        },
    )

    private val _state = MutableStateFlow(RemoteUiState())
    val state: StateFlow<RemoteUiState> = _state.asStateFlow()

    val devices: StateFlow<List<DiscoveredTv>> = discovery.tvs
    val searching: StateFlow<Boolean> = discovery.searching

    private val pingSamples = ArrayDeque<Int>()

    // ---- Discovery ----------------------------------------------------------------------------

    fun startDiscovery() = discovery.start()
    fun stopDiscovery() = discovery.stop()
    fun refreshDiscovery() = discovery.refresh()

    fun addManualAddress(address: String): DiscoveredTv? = discovery.addManual(address)

    // ---- Connection ---------------------------------------------------------------------------

    fun connect(tv: DiscoveredTv) {
        _state.value = _state.value.copy(tv = tv, message = null)
        client.connect(tv)
    }

    fun forgetTv() {
        client.disconnect()
        _state.value = RemoteUiState()
    }

    fun submitPairingCode(code: String) {
        val digits = code.filter { it.isDigit() }.take(6)
        if (digits.length != 6) return
        client.send(Protocol.pair(digits, DeviceIdentity.id(context), DeviceIdentity.name(context), "android"))
    }

    fun foregrounded() = client.retryNow()

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    // ---- Input --------------------------------------------------------------------------------

    fun key(key: Protocol.Key, hold: Boolean = false) = client.send(Protocol.key(key, hold))
    fun pointer(dx: Float, dy: Float) = client.send(Protocol.pointer(dx, dy))
    fun pointerBatch(samples: List<Protocol.Sample>) = client.send(Protocol.pointerBatch(samples))
    fun click() = client.send(Protocol.click())
    fun longPress() = client.send(Protocol.longPress())
    fun scroll(dy: Float) = client.send(Protocol.scroll(dy))
    fun type(text: String) = client.send(Protocol.text(text))
    fun backspace(count: Int = 1) = client.send(Protocol.backspace(count))
    fun submit() = client.send(Protocol.submit())
    fun voice(transcription: String) {
        client.send(Protocol.voice(transcription))
        client.send(Protocol.submit())
    }
    fun launch(packageName: String) = client.send(Protocol.launch(packageName))

    // ---- Incoming -----------------------------------------------------------------------------

    private fun update(message: Protocol.ServerMessage) {
        when (message) {
            is Protocol.ServerMessage.Welcome -> {
                // A fresh handshake means the TV accepted us: clear the pairing prompts and the RTT
                // history so the connection indicator starts from the new link.
                pingSamples.clear()
                _state.value = _state.value.copy(pairingAttempts = 0, pairingRequired = false, message = null)
            }

            is Protocol.ServerMessage.Paired -> _state.value = _state.value.copy(pairingAttempts = 0)

            is Protocol.ServerMessage.PairRequired ->
                _state.value = _state.value.copy(pairingRequired = true)

            is Protocol.ServerMessage.Error -> _state.value = _state.value.copy(
                message = message.message,
                // The TV reports how many tries are left, so the count down is its number, not ours.
                pairingAttempts = message.attempts?.let { 5 - it } ?: _state.value.pairingAttempts,
            )

            is Protocol.ServerMessage.State -> _state.value = _state.value.copy(tvState = message.state)

            is Protocol.ServerMessage.Apps -> _state.value = _state.value.copy(apps = message.apps)

            is Protocol.ServerMessage.Toast -> _state.value = _state.value.copy(message = message.text)

            is Protocol.ServerMessage.Event -> {
                if (message.name == "paired-elsewhere" || message.name == "revoked") {
                    client.disconnect()
                    _state.value = RemoteUiState(message = "This phone was removed from the TV.")
                }
            }

            is Protocol.ServerMessage.Pong -> {
                val rtt = (System.currentTimeMillis() - message.ts).coerceAtLeast(0).toInt()
                pingSamples.addLast(rtt)
                while (pingSamples.size > 5) pingSamples.removeFirst()
                _state.value = _state.value.copy(roundTripMs = pingSamples.average().toInt())
            }
        }
    }

    override fun onCleared() {
        discovery.stop()
        client.disconnect()
        super.onCleared()
    }
}
