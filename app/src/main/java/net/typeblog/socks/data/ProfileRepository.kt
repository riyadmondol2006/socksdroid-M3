package net.typeblog.socks.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Stores profiles in the SharedPreferences file used by SocksDroid 1.x. Each profile is kept as
 * JSON under a key derived verbatim from its name; profiles still in the 1.x per-field key layout
 * are read transparently and converted on their next save. Safe to use from any thread.
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
        migrateLegacyList()
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
                .also { require(isValidName(it.name)) { "Profile without a name" } }
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

    /** 1.x kept an implicit "Default" profile outside the stored list; make it explicit. */
    private fun migrateLegacyList() {
        if (prefs.contains(KEY_LIST)) return
        val legacy = prefs.getString(LEGACY_KEY_LIST, null).orEmpty().split('\n').filter { it.isNotEmpty() }
        prefs.edit { putString(KEY_LIST, (listOf(DEFAULT_NAME) + legacy).distinct().joinToString("\n")) }
    }

    private fun read(name: String): Profile {
        prefs.getString(KEY_JSON_PREFIX + name, null)?.let { json ->
            runCatching { return Profile.fromJson(org.json.JSONObject(json)).copy(name = name) }
        }
        return readLegacy(name)
    }

    private fun readLegacy(name: String): Profile {
        val k = KeyPrefix(name)
        val d = Profile(name = name)
        return Profile(
            name = name,
            server = prefs.getString(k("server"), d.server)!!,
            port = prefs.getInt(k("port"), d.port),
            useAuth = prefs.getBoolean(k("userpw"), d.useAuth),
            username = prefs.getString(k("username"), d.username)!!,
            password = prefs.getString(k("password"), d.password)!!,
            route = RouteMode.fromKey(prefs.getString(k("route"), d.route.key)),
            bypassLan = prefs.getBoolean(k("bypasslan"), d.bypassLan),
            dns = prefs.getString(k("dns"), d.dns)!!,
            dnsPort = prefs.getInt(k("dns_port"), d.dnsPort),
            perApp = prefs.getBoolean(k("perapp"), d.perApp),
            bypassApps = prefs.getBoolean(k("appbypass"), d.bypassApps),
            apps = prefs.getString(k("applist"), "")!!.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
            ipv6 = prefs.getBoolean(k("ipv6"), d.ipv6),
            udp = prefs.getBoolean(k("udp"), d.udp),
            udpGateway = prefs.getString(k("udpgw"), d.udpGateway)!!,
        )
    }

    private fun write(e: SharedPreferences.Editor, p: Profile) {
        val normalized = p.copy(server = p.server.trim(), dns = p.dns.trim(), udpGateway = p.udpGateway.trim())
        removeLegacyKeys(e, p.name)
        e.putString(KEY_JSON_PREFIX + p.name, normalized.toJson().toString())
    }

    private fun removeKeys(e: SharedPreferences.Editor, name: String) {
        removeLegacyKeys(e, name)
        e.remove(KEY_JSON_PREFIX + name)
    }

    private fun removeLegacyKeys(e: SharedPreferences.Editor, name: String) {
        val k = KeyPrefix(name)
        // The 1.x scheme is ambiguous ("a  b" and "a_b" share keys); only clear it for profiles that use it.
        if (!prefs.contains(KEY_JSON_PREFIX + name)) LEGACY_PROFILE_KEYS.forEach { e.remove(k(it)) }
    }

    /** 1.x key scheme: "_" is escaped to "__" and " " becomes "_". */
    private class KeyPrefix(name: String) {
        private val prefix = name.replace("_", "__").replace(" ", "_")
        operator fun invoke(key: String) = prefix + key
    }

    companion object {
        const val DEFAULT_NAME = "Default"
        private const val PREFS_NAME = "profile"
        private const val LEGACY_KEY_LIST = "profile"
        private const val KEY_LIST = "profiles_v2"
        private const val KEY_ACTIVE = "last_profile"
        private const val KEY_JSON_PREFIX = "profile_json:"
        private val LEGACY_PROFILE_KEYS = listOf(
            "server", "port", "userpw", "username", "password", "route", "bypasslan", "dns",
            "dns_port", "perapp", "appbypass", "applist", "ipv6", "udp", "udpgw", "auto",
        )

        /** Collapses whitespace (including newlines, which would corrupt the stored list) and trims. */
        fun sanitizeName(name: String): String = name.replace(Regex("\\s+"), " ").trim()

        fun isValidName(name: String): Boolean = name.isNotEmpty() && name == sanitizeName(name)
    }
}
