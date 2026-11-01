package dev.couchtv.launcher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * The Wi-Fi name and how good the link is, for the header pill and for the sideload/remote screens
 * (which need an address a computer on the same network can open).
 *
 * On Android 10+ the SSID is only readable with location permission, which is why requesting it is
 * optional: without it the row simply says "Wi-Fi" and the address still works.
 */
data class NetworkStatus(
    val ssid: String?,
    val connected: Boolean,
    /** 0..4 bars, or -1 when unknown (Ethernet, no permission). */
    val signalBars: Int,
    val address: String?,
    val isWifi: Boolean,
) {
    val label: String
        get() = when {
            !connected -> "Offline"
            isWifi -> ssid ?: "Wi-Fi"
            else -> "Ethernet"
        }
}

object NetworkInfo {

    fun status(context: Context): NetworkStatus {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        var connected = false
        var isWifi = false
        if (connectivity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivity.activeNetwork
                val capabilities = network?.let(connectivity::getNetworkCapabilities)
                connected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                connected = connectivity.activeNetworkInfo?.isConnected == true
                @Suppress("DEPRECATION")
                isWifi = connectivity.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        }

        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val ssid = when {
            !isWifi || !hasLocation -> null
            else -> runCatching {
                @Suppress("DEPRECATION")
                wifiManager?.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            }.getOrNull()
        }

        val bars = when {
            !isWifi -> -1
            else -> runCatching {
                @Suppress("DEPRECATION")
                val rssi = wifiManager?.connectionInfo?.rssi ?: return@runCatching -1
                WifiManager.calculateSignalLevel(rssi, 5)
            }.getOrDefault(-1)
        }

        return NetworkStatus(ssid, connected, bars, localAddress(), isWifi)
    }

    /**
     * The address a phone or laptop on the same network can reach, preferring IPv4 on wlan0/eth0.
     * Loopback and link-local addresses are never returned.
     */
    fun localAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()
            ?.filter { it.isUp && !it.isLoopback }
            ?.sortedByDescending { it.name.startsWith("wlan") || it.name.startsWith("eth") }
            ?.asSequence()
            ?.flatMap { it.inetAddresses.toList().asSequence() }
            ?.filterIsInstance<Inet4Address>()
            ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }.getOrNull()
}
