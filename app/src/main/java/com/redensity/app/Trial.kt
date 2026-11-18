package com.redensity.app

import android.content.Context

/**
 * Snapshot setelan layar sebelum uji coba diterapkan.
 * Disimpan di SharedPreferences agar bisa direvert walau proses app mati.
 */
object Trial {

    private const val PREFS = "redensity"
    private const val KEY_PENDING = "trial_pending"
    private const val KEY_SIZE_RAW = "trial_size_raw"
    private const val KEY_DENSITY_RAW = "trial_density_raw"
    private const val KEY_LABEL = "trial_label"
    private const val KEY_EXPIRES = "trial_expires_at"

    data class Snapshot(
        val sizeRaw: String,
        val densityRaw: String,
        val label: String,
        val expiresAt: Long
    )

    fun save(context: Context, snapshot: Snapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .putString(KEY_SIZE_RAW, snapshot.sizeRaw)
            .putString(KEY_DENSITY_RAW, snapshot.densityRaw)
            .putString(KEY_LABEL, snapshot.label)
            .putLong(KEY_EXPIRES, snapshot.expiresAt)
            .commit()
    }

    fun load(context: Context): Snapshot? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PENDING, false)) return null
        return Snapshot(
            sizeRaw = prefs.getString(KEY_SIZE_RAW, "") ?: "",
            densityRaw = prefs.getString(KEY_DENSITY_RAW, "") ?: "",
            label = prefs.getString(KEY_LABEL, "") ?: "",
            expiresAt = prefs.getLong(KEY_EXPIRES, 0L)
        )
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .remove(KEY_SIZE_RAW)
            .remove(KEY_DENSITY_RAW)
            .remove(KEY_LABEL)
            .remove(KEY_EXPIRES)
            .commit()
    }

    fun hasOverride(raw: String, label: String): Boolean = raw.contains("Override")

    fun parseLastSize(raw: String): Pair<Int, Int>? {
        val matches = Regex("""(\d+)x(\d+)""").findAll(raw).toList()
        val last = matches.lastOrNull() ?: return null
        return Pair(last.groupValues[1].toInt(), last.groupValues[2].toInt())
    }

    fun parseLastNumber(raw: String): Int? =
        Regex("""(\d+)""").findAll(raw).toList().lastOrNull()?.value?.toInt()

    /** Perintah restore: kembalikan override lama, atau reset bila memang tidak ada override. */
    fun sizeRestoreCommand(sizeRaw: String): Array<String> {
        return if (sizeRaw.contains("Override")) {
            val s = parseLastSize(sizeRaw)
            if (s != null) arrayOf("wm", "size", "${s.first}x${s.second}") else arrayOf("wm", "size", "reset")
        } else {
            arrayOf("wm", "size", "reset")
        }
    }

    fun densityRestoreCommand(densityRaw: String): Array<String> {
        return if (densityRaw.contains("Override")) {
            val d = parseLastNumber(densityRaw)
            if (d != null) arrayOf("wm", "density", d.toString()) else arrayOf("wm", "density", "reset")
        } else {
            arrayOf("wm", "density", "reset")
        }
    }
}
