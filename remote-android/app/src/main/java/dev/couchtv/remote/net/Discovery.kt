package dev.couchtv.remote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One TV on the LAN, as the remote understands it.
 *
 * `manual` marks an address typed by the user, which is the fallback when multicast is blocked.
 */
data class DiscoveredTv(
    val id: String,
    val name: String,
    val model: String,
    val host: String,
    val port: Int,
    val pairing: Boolean,
    val manual: Boolean = false,
) {
    val address: String get() = "$host:$port"
    val websocketUrl: String get() = "ws://$host:$port/remote"
    fun iconUrl(packageName: String): String = "http://$host:$port/icon?pkg=$packageName"
}

/**
 * Bonjour discovery through Android's own NSD stack, with a multicast lock held only while browsing.
 *
 * NSD is flaky on a few devices, and its callbacks arrive on background threads: both are handled
 * here rather than leaking into the UI. The screen always offers manual entry, so a failed discovery
 * degrades to a text field rather than a dead end.
 */
class Discovery(private val context: Context) {

    private val _tvs = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    val tvs: StateFlow<List<DiscoveredTv>> = _tvs.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val manager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val multicastLock: WifiManager.MulticastLock? =
        (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("couchremote.discovery")
            ?.apply { setReferenceCounted(true) }

    private val listeners = mutableMapOf<String, NsdManager.ResolveListener>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val manual = mutableListOf<DiscoveredTv>()

    fun start() {
        if (_searching.value) return
        val nsd = manager ?: return

        runCatching { multicastLock?.acquire() }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _searching.value = true
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.startsWith(SERVICE_TYPE)) return
                resolve(nsd, service)
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                _tvs.value = _tvs.value.filterNot {
                    it.id == service.serviceName && !it.manual
                } + manual
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _searching.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // Typically "already active" or a multicast problem; manual entry still works.
                _searching.value = false
                runCatching { nsd.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { nsd.stopServiceDiscovery(this) }
                _searching.value = false
            }
        }

        discoveryListener = listener
        runCatching { nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { _searching.value = false }
    }

    fun stop() {
        discoveryListener?.let { listener -> runCatching { manager?.stopServiceDiscovery(listener) } }
        discoveryListener = null
        listeners.clear()
        runCatching { if (multicastLock?.isHeld == true) multicastLock.release() }
        _searching.value = false
    }

    /** Restarts discovery; safe to call from a button. */
    fun refresh() {
        stop()
        _tvs.value = manual.toList()
        start()
    }

    /** Accepts "192.168.1.42", "192.168.1.42:8765" or "[fe80::1]:8765". */
    fun addManual(address: String): DiscoveredTv? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null

        var host = trimmed
        var port = DEFAULT_PORT
        when {
            trimmed.startsWith("[") && trimmed.contains("]") -> {
                val closing = trimmed.indexOf(']')
                host = trimmed.substring(1, closing)
                val rest = trimmed.substring(closing + 1)
                if (rest.startsWith(":")) port = rest.drop(1).toIntOrNull() ?: return null
            }
            trimmed.count { it == ':' } == 1 -> {
                val separator = trimmed.lastIndexOf(':')
                host = trimmed.substring(0, separator)
                port = trimmed.substring(separator + 1).toIntOrNull() ?: return null
            }
        }
        if (host.isBlank() || port !in 1..65535) return null

        val tv = DiscoveredTv(
            id = "manual:$host:$port",
            name = host,
            model = "Entered by hand",
            host = host,
            port = port,
            pairing = false,
            manual = true,
        )
        manual.removeAll { it.id == tv.id }
        manual += tv
        _tvs.value = _tvs.value.filterNot { it.id == tv.id } + tv
        return tv
    }

    private fun resolve(nsd: NsdManager, service: NsdServiceInfo) {
        if (listeners.containsKey(service.serviceName)) return

        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                listeners.remove(serviceInfo.serviceName)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                listeners.remove(serviceInfo.serviceName)
                val host = serviceInfo.host?.hostAddress ?: return
                val tv = DiscoveredTv(
                    id = attributes(serviceInfo)["id"] ?: serviceInfo.serviceName,
                    name = attributes(serviceInfo)["name"] ?: serviceInfo.serviceName,
                    model = attributes(serviceInfo)["model"] ?: "Android TV",
                    host = host,
                    port = serviceInfo.port,
                    pairing = attributes(serviceInfo)["pair"] == "1",
                )
                _tvs.value = (_tvs.value.filterNot { it.id == tv.id } + tv)
                    .sortedWith(compareBy({ !it.manual }, { it.name.lowercase() }))
            }
        }

        listeners[service.serviceName] = listener
        runCatching { nsd.resolveService(service, listener) }
            .onFailure { listeners.remove(service.serviceName) }
    }

    /** NSD hands TXT records over as raw bytes; the protocol stores UTF-8 strings. */
    private fun attributes(service: NsdServiceInfo): Map<String, String> =
        service.attributes.mapValues { (_, value) -> String(value, Charsets.UTF_8) }

    private companion object {
        const val SERVICE_TYPE = "_couchremote._tcp."
        const val DEFAULT_PORT = 8765
    }
}
