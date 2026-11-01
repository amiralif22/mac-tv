package dev.couchtv.launcher.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Announces the TV as `_couchremote._tcp` so the phone apps find it without an IP address.
 *
 * The service is registered while the launcher is running (including with the screen off, so a phone
 * can wake it). Android's NSD implementation is occasionally flaky — the "service already registered"
 * and "internal error" cases are common after a network change — so failures are logged and retried
 * once rather than surfaced, and the remote itself keeps working over a manual address.
 */
class MdnsAdvertiser(private val context: Context) {

    private var manager: NsdManager? = null
    private var listener: NsdManager.RegistrationListener? = null
    private var pending: Triple<Int, String, Map<String, String>>? = null

    fun start(port: Int, name: String, txt: Map<String, String>): Boolean {
        pending = Triple(port, name, txt)
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return false
        manager = nsd
        return register(nsd, port, name, txt)
    }

    private fun register(nsd: NsdManager, port: Int, name: String, txt: Map<String, String>): Boolean {
        stopListener()
        val info = NsdServiceInfo().apply {
            serviceName = name.take(48)
            serviceType = SERVICE_TYPE
            setPort(port)
            txt.forEach { (key, value) -> runCatching { setAttribute(key, value) } }
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "Announced as ${info.serviceName}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                // One retry after a network change is usually enough; beyond that, manual entry works.
                Log.w(TAG, "mDNS registration failed ($errorCode)")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = registration
        return runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registration)
            true
        }.getOrElse {
            Log.w(TAG, "Couldn't start mDNS: ${it.message}")
            false
        }
    }

    fun stop() {
        stopListener()
        pending = null
        manager = null
    }

    private fun stopListener() {
        val registration = listener ?: return
        listener = null
        runCatching { manager?.unregisterService(registration) }
    }

    companion object {
        const val SERVICE_TYPE = "_couchremote._tcp"
        private const val TAG = "CouchTv/mdns"
    }
}
