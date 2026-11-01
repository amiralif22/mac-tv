package dev.couchtv.launcher.remote

import android.app.admin.DeviceAdminReceiver

/**
 * Declared so the launcher *can* be made device owner on a TV box (Settings › Remote › Screen off).
 * Without it, the remote's power button can wake the screen but not turn it off. Nothing is enforced:
 * no password policy, no wipe capability is used.
 */
class RemoteDeviceAdmin : DeviceAdminReceiver()
