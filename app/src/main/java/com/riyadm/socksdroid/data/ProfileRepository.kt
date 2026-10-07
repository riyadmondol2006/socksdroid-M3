package com.riyadm.socksdroid.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Stores profiles in SharedPreferences, each as JSON under a key taken verbatim from its name,
 * plus the ordered name list and the active profile. Safe to use from any thread.
 */
class ProfileRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow(emptyList<Profile>())

    /** All profiles, in display order. Never empty. */
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeName = MutableStateFlow("")

    /** Name of the profile used when connecting. */
    val activeName: StateFlow<String> = _activeName.asStateFlow()

    init {
        if (!prefs.contains(KEY_LIST)) prefs.edit { putString(KEY_LIST, DEFAULT_NAME) }
        refresh()
    }

    val active: Profile
        get() = profiles.value.firstOrNull { it.name == activeName.value } ?: profiles.value.first()

    fun get(name: String): Profile? = profiles.value.firstOrNull { it.name == name }

    @Synchronized
    fun setActive(name: String) {
        if (name in names()) {
            prefs.edit { putString(KEY_ACTIVE, name) }
            refresh()
        }
    }

    /** Inserts or updates [profile] (matched by name). New profiles are appended. */
    @Synchronized
    fun save(profile: Profile) {
        require(isValidName(profile.name)) { "Invalid profile name" }
        val names = names()
        prefs.edit {
            write(this, profile)
            if (profile.name !in names) putString(KEY_LIST, (names + profile.name).joinToString("\n"))
        }
        refresh()
    }

    /** Creates a new profile with default settings. Returns null if [name] is blank or already taken. */
    fun create(name: String): Profile? {
        val trimmed = sanitizeName(name)
        if (!isValidName(trimmed) || trimmed in names()) return null
        return Profile(name = trimmed).also(::save)
    }

    /** Renames a profile, keeping its settings. Returns false if [newName] is invalid or taken. */
    @Synchronized
    fun rename(oldName: String, newName: String): Boolean {
        val trimmed = sanitizeName(newName)
        val names = names()
        val profile = get(oldName) ?: return false
        if (!isValidName(trimmed) || trimmed in names) return false
        prefs.edit {
            removeKeys(this, oldName)
            write(this, profile.copy(name = trimmed))
            putString(KEY_LIST, names.map { if (it == oldName) trimmed else it }.joinToString("\n"))
            if (activeName.value == oldName) putString(KEY_ACTIVE, trimmed)
        }
        refresh()
        return true
    }

    /** Copies a profile under a unique name and returns the copy. */
    fun duplicate(name: String): Profile? {
        val source = get(name) ?: return null
        return source.copy(name = uniqueName(source.name)).also(::save)
    }

    /** Deletes a profile. The last remaining profile cannot be deleted. */
    @Synchronized
    fun delete(name: String): Boolean {
        val names = names()
        if (name !in names || names.size <= 1) return false
        prefs.edit {
            removeKeys(this, name)
            putString(KEY_LIST, (names - name).joinToString("\n"))
            if (activeName.value == name) remove(KEY_ACTIVE)
        }
        refresh()
        return true
    }

    fun uniqueName(base: String): String {
        val names = names().toSet()
        if (base !in names) return base
        var i = 2
        while ("$base ($i)" in names) i++
        return "$base ($i)"
    }

    /** Serializes every profile as a JSON array. */
    fun exportJson(): String = JSONArray(profiles.value.map { it.toJson() }).toString(2)

    /**
     * Imports profiles from [json] (an array, or a single object). Name clashes get a numeric suffix.
     * Either every profile is imported or, if any entry is invalid, none is (the parse error is thrown).
     * Returns the number of imported profiles.
     */
    @Synchronized
    fun importJson(json: String): Int {
        val trimmed = json.trim()
        val objects = if (trimmed.startsWith("[")) {
            JSONArray(trimmed).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
        } else {
            listOf(org.json.JSONObject(trimmed))
        }
        val imported = objects.map { o ->
            Profile.fromJson(o).let { it.copy(name = sanitizeName(it.name)) }
                .also { require(isValidName(it.name) && it.isValid) { "Invalid profile \"${it.name}\"" } }
        }
        imported.forEach { save(it.copy(name = uniqueName(it.name))) }
        return imported.size
    }

    private fun names(): List<String> =
        prefs.getString(KEY_LIST, null).orEmpty().split('\n').filter { it.isNotEmpty() }

    @Synchronized
    private fun refresh() {
        val list = names().map(::read)
        _profiles.value = list
        val active = prefs.getString(KEY_ACTIVE, null)
        _activeName.value = if (list.any { it.name == active }) active!! else list.first().name
    }

    private fun read(name: String): Profile =
        prefs.getString(KEY_JSON_PREFIX + name, null)
            ?.let { json -> runCatching { Profile.fromJson(org.json.JSONObject(json)).copy(name = name) }.getOrNull() }
            ?: Profile(name = name)

    private fun write(e: SharedPreferences.Editor, p: Profile) {
        val normalized = p.copy(server = p.server.trim(), dns = p.dns.trim(), udpGateway = p.udpGateway.trim())
        e.putString(KEY_JSON_PREFIX + p.name, normalized.toJson().toString())
    }

    private fun removeKeys(e: SharedPreferences.Editor, name: String) {
        e.remove(KEY_JSON_PREFIX + name)
    }

    companion object {
        const val DEFAULT_NAME = "Default"
        private const val PREFS_NAME = "profiles"
        private const val KEY_LIST = "names"
        private const val KEY_ACTIVE = "active"
        private const val KEY_JSON_PREFIX = "profile:"
        /** Collapses whitespace (including newlines, which would corrupt the stored list) and trims. */
        fun sanitizeName(name: String): String = name.replace(Regex("\\s+"), " ").trim()

        fun isValidName(name: String): Boolean = name.isNotEmpty() && name == sanitizeName(name)
    }
}
