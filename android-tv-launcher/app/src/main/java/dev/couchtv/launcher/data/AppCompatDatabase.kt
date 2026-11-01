package dev.couchtv.launcher.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The local, persistent answer to "does this package work with a remote?".
 *
 * Scanning every installed package's flags costs tens of milliseconds on a weak TV box, and the
 * answer does not change until the package is updated — so the result is stored here, keyed by
 * package name and install time. A reinstall or update changes [installedAt] and triggers a rescan.
 *
 * SQLite (rather than Room) on purpose: no annotation processor, no KSP, ~40 KB of APK.
 */
class AppCompatDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE compat (
                package_name TEXT PRIMARY KEY NOT NULL,
                version_code INTEGER NOT NULL,
                label TEXT NOT NULL,
                status TEXT NOT NULL,
                banner INTEGER NOT NULL DEFAULT 0,
                leanback INTEGER NOT NULL DEFAULT 0,
                checked_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The table is a cache: rebuilding it is always correct and never loses user choices.
        if (oldVersion < 2) {
            db.execSQL("DROP TABLE IF EXISTS compat")
            onCreate(db)
        }
    }

    /** A stored verdict plus the evidence, for the settings screen's explanation line. */
    data class Record(
        val packageName: String,
        val versionCode: Long,
        val label: String,
        val status: TvCompat,
        val hasBanner: Boolean,
        val hasLeanback: Boolean,
        val checkedAt: Long,
    )

    fun get(packageName: String, versionCode: Long): Record? = readableDatabase.query(
        TABLE, null, "package_name = ? AND version_code = ?", arrayOf(packageName, versionCode.toString()),
        null, null, null
    ).use { cursor ->
        if (!cursor.moveToFirst()) return null
        Record(
            packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
            versionCode = cursor.getLong(cursor.getColumnIndexOrThrow("version_code")),
            label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
            status = runCatching {
                TvCompat.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("status")))
            }.getOrDefault(TvCompat.UNKNOWN),
            hasBanner = cursor.getInt(cursor.getColumnIndexOrThrow("banner")) == 1,
            hasLeanback = cursor.getInt(cursor.getColumnIndexOrThrow("leanback")) == 1,
            checkedAt = cursor.getLong(cursor.getColumnIndexOrThrow("checked_at")),
        )
    }

    fun put(record: Record) {
        val values = ContentValues().apply {
            put("package_name", record.packageName)
            put("version_code", record.versionCode)
            put("label", record.label)
            put("status", record.status.name)
            put("banner", if (record.hasBanner) 1 else 0)
            put("leanback", if (record.hasLeanback) 1 else 0)
            put("checked_at", record.checkedAt)
        }
        // Cache write: a failure here only costs a rescan later, never correctness.
        runCatching { writableDatabase.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    fun putAll(records: Collection<Record>) {
        if (records.isEmpty()) return
        runCatching {
            writableDatabase.beginTransaction()
            records.forEach(::put)
            writableDatabase.setTransactionSuccessful()
        }
        runCatching { writableDatabase.endTransaction() }
    }

    /** Forgets packages that are no longer installed, so the table cannot grow forever. */
    fun pruneExcept(packageNames: Set<String>) {
        if (packageNames.isEmpty()) return
        val placeholders = packageNames.joinToString(",") { "?" }
        runCatching {
            writableDatabase.delete(TABLE, "package_name NOT IN ($placeholders)", packageNames.toTypedArray())
        }
    }

    /** The user's own correction, from Manage Apps: remembered forever for that package. */
    fun setUserVerdict(packageName: String, versionCode: Long, status: TvCompat) {
        val existing = get(packageName, versionCode) ?: Record(
            packageName = packageName,
            versionCode = versionCode,
            label = packageName,
            status = status,
            hasBanner = false,
            hasLeanback = false,
            checkedAt = System.currentTimeMillis(),
        )
        put(existing.copy(status = status, checkedAt = System.currentTimeMillis()))
    }

    private companion object {
        const val NAME = "app_compat.db"
        const val VERSION = 2
        const val TABLE = "compat"
    }
}
