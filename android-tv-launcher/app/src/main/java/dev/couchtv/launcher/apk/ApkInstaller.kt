package dev.couchtv.launcher.apk

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.coroutines.resume

/**
 * Installs an APK the launcher already has on disk (picked with the file picker, or uploaded from a
 * computer) using the platform's own installer session.
 *
 * Why sessions rather than an `ACTION_VIEW` intent: a session reports a real result. The user sees
 * the system's confirmation dialog, and we get STATUS_SUCCESS or a specific failure we can translate
 * into a sentence, instead of the launcher simply disappearing while another app takes over.
 *
 * Failure is always a value, never an exception: [Result.Failed] carries a message the UI can show.
 */
class ApkInstaller(private val context: Context) {

    sealed class Result {
        data class Installed(val packageName: String) : Result()
        data class Failed(val message: String) : Result()
        data object NeedsPermission : Result()
    }

    /** Android 8+ requires the user to allow installs from this app once. */
    fun canInstall(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    } else {
        true
    }

    fun permissionSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        return runCatching {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()
    }

    /**
     * Installs [file]. Suspends until the system installer reports back, or for three minutes.
     * [knownPackageName] comes from [ApkInspector] and is only used to label the result.
     */
    suspend fun install(file: File, knownPackageName: String? = null): Result = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() == 0L) return@withContext Result.Failed("That file is empty.")
        if (!canInstall()) return@withContext Result.NeedsPermission

        val installer = context.packageManager.packageInstaller
        val sessionId = runCatching {
            installer.createSession(
                PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setSize(file.length())
                }
            )
        }.getOrElse { error ->
            return@withContext Result.Failed(error.message ?: "The installer refused the session.")
        }

        val outcome = withTimeoutOrNull(180_000L) { await(sessionId, file, installer, knownPackageName) }
        if (outcome == null) {
            runCatching { installer.abandonSession(sessionId) }
            return@withContext Result.Failed("The install took too long. Try again.")
        }
        outcome
    }

    private suspend fun await(
        sessionId: Int,
        file: File,
        installer: PackageInstaller,
        knownPackageName: String?,
    ): Result = suspendCancellableCoroutine { continuation ->
        val action = "${context.packageName}.INSTALL_RESULT.$sessionId"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                    ?: knownPackageName.orEmpty()
                val result = resultFor(status, packageName, message, intent)
                // A pending-user-action broadcast is followed by a real one, so keep listening.
                if (result !is FailedWait) runCatching { receiverContext.unregisterReceiver(this) }
                if (continuation.isActive) continuation.resume(result)
            }
        }

        val registered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess

        if (!registered) {
            runCatching { installer.abandonSession(sessionId) }
            continuation.resume(Result.Failed("Couldn't listen for the install result."))
            return@suspendCancellableCoroutine
        }

        continuation.invokeOnCancellation {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { installer.abandonSession(sessionId) }
        }

        runCatching {
            // Write the APK into the session, then ask the system to commit it.
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 8)
                        session.fsync(output)
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    Intent(action).setPackage(context.packageName),
                    flags,
                )
                session.commit(pending.intentSender)
            }
        }.onFailure { error ->
            runCatching { installer.abandonSession(sessionId) }
            if (continuation.isActive) {
                continuation.resume(Result.Failed(error.message ?: "Couldn't write the APK to the installer."))
            }
        }
    }

    private fun resultFor(status: Int, packageName: String, message: String, intent: Intent): Result = when (status) {
        PackageInstaller.STATUS_SUCCESS -> Result.Installed(packageName)
        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
            // The system wants to show its confirmation. Launch it and let the session continue;
            // the next broadcast will carry the real outcome.
            val confirm = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            val launched = runCatching {
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }.isSuccess
            if (launched) FailedWait() else Result.Failed("Couldn't show the install dialog.")
        }

        PackageInstaller.STATUS_FAILURE_ABORTED -> Result.Failed("The install was cancelled.")
        PackageInstaller.STATUS_FAILURE_BLOCKED -> Result.Failed("The install was blocked. Allow unknown sources and try again.")
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            Result.Failed("An app with the same name is already installed with a different signature.")

        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> Result.Failed("That APK doesn't fit this device.")
        PackageInstaller.STATUS_FAILURE_INVALID -> Result.Failed("That file isn't a valid APK.")
        PackageInstaller.STATUS_FAILURE_STORAGE -> Result.Failed("Not enough space to install.")
        else -> Result.Failed(message.ifBlank { "The installer failed ($status)." })
    }

    /**
     * The confirmation dialog is open; no result yet. Treated as "nothing to report" by the UI, which
     * keeps listening for the next broadcast through the same receiver.
     */
    class FailedWait : Result() {
        override fun equals(other: Any?): Boolean = other is FailedWait
        override fun hashCode(): Int = 0
    }
}
