package com.redensity.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Profil buatan user, disimpan di profiles.json (internal storage). */
object Profiles {

    data class Profile(
        val name: String,
        val widthPx: Int,
        val density: Int
    )

    private const val FILE = "profiles.json"

    /** Tidak ada profil bawaan — hanya profil buatan user di profiles.json. */
    fun loadAll(context: Context): List<Profile> = loadUser(context)

    private fun file(context: Context) = File(context.filesDir, FILE)

    fun loadUser(context: Context): MutableList<Profile> {
        val result = mutableListOf<Profile>()
        val f = file(context)
        if (!f.exists()) return result
        return try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(Profile(o.getString("name"), o.getInt("widthPx"), o.getInt("density")))
            }
            result
        } catch (_: Exception) {
            emptyList<Profile>().toMutableList()
        }
    }

    fun saveUser(context: Context, profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().put("name", p.name).put("widthPx", p.widthPx).put("density", p.density))
        }
        file(context).writeText(arr.toString())
    }

    fun addUser(context: Context, profile: Profile) {
        val list = loadUser(context)
        list.removeAll { it.name.equals(profile.name, ignoreCase = true) }
        list.add(profile)
        saveUser(context, list)
    }

    fun deleteUser(context: Context, name: String) {
        saveUser(context, loadUser(context).filterNot { it.name.equals(name, ignoreCase = true) })
    }
}
