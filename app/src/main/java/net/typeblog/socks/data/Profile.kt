package net.typeblog.socks.data

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

enum class RouteMode(val key: String) {
    /** Route every IPv4 address through the proxy. */
    ALL("all"),

    /** Route everything except mainland China IP ranges. */
    NON_CHINA("chn");

    companion object {
        fun fromKey(key: String?): RouteMode = entries.firstOrNull { it.key == key } ?: ALL
    }
}

/**
 * A SOCKS5 server profile. Immutable; edit with [copy] and persist via [ProfileRepository.save].
 */
data class Profile(
    val name: String,
    val server: String = "127.0.0.1",
    val port: Int = 1080,
    val useAuth: Boolean = false,
    val username: String = "",
    val password: String = "",
    val route: RouteMode = RouteMode.ALL,
    val bypassLan: Boolean = true,
    val dns: String = "8.8.8.8",
    val dnsPort: Int = 53,
    /** Send DNS queries through the SOCKS5 server instead of directly to [dns]. */
    val remoteDns: Boolean = true,
    val perApp: Boolean = false,
    /** When [perApp] is set: true = listed apps bypass the proxy, false = only listed apps are proxied. */
    val bypassApps: Boolean = false,
    val apps: Set<String> = emptySet(),
    val ipv6: Boolean = false,
    val udp: Boolean = false,
    val udpGateway: String = "127.0.0.1:7300",
) {
    val endpoint: String
        get() = if (server.contains(':')) "[$server]:$port" else "$server:$port"

    /** `socks5://user:pass@host:port#name`, suitable for sharing. */
    fun toUri(includeCredentials: Boolean = true): String = Uri.Builder()
        .scheme("socks5")
        .encodedAuthority(buildString {
            if (useAuth && includeCredentials && username.isNotEmpty()) {
                append(Uri.encode(username))
                if (password.isNotEmpty()) append(':').append(Uri.encode(password))
                append('@')
            }
            append(endpoint)
        })
        .fragment(name)
        .build()
        .toString()

    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("server", server)
        .put("port", port)
        .put("useAuth", useAuth)
        .put("username", username)
        .put("password", password)
        .put("route", route.key)
        .put("bypassLan", bypassLan)
        .put("dns", dns)
        .put("dnsPort", dnsPort)
        .put("remoteDns", remoteDns)
        .put("perApp", perApp)
        .put("bypassApps", bypassApps)
        .put("apps", JSONArray(apps.sorted()))
        .put("ipv6", ipv6)
        .put("udp", udp)
        .put("udpGateway", udpGateway)

    companion object {
        fun fromJson(o: JSONObject): Profile {
            val d = Profile(name = o.getString("name"))
            val apps = o.optJSONArray("apps")
            return Profile(
                name = d.name,
                server = o.optString("server", d.server),
                port = o.optInt("port", d.port),
                useAuth = o.optBoolean("useAuth", d.useAuth),
                username = o.optString("username", d.username),
                password = o.optString("password", d.password),
                route = RouteMode.fromKey(o.optString("route", d.route.key)),
                bypassLan = o.optBoolean("bypassLan", d.bypassLan),
                dns = o.optString("dns", d.dns),
                dnsPort = o.optInt("dnsPort", d.dnsPort),
                remoteDns = o.optBoolean("remoteDns", d.remoteDns),
                perApp = o.optBoolean("perApp", d.perApp),
                bypassApps = o.optBoolean("bypassApps", d.bypassApps),
                apps = if (apps == null) emptySet() else (0 until apps.length()).map { apps.getString(it) }.toSet(),
                ipv6 = o.optBoolean("ipv6", d.ipv6),
                udp = o.optBoolean("udp", d.udp),
                udpGateway = o.optString("udpGateway", d.udpGateway),
            )
        }

        /**
         * Parses `socks5://[user[:pass]@]host[:port][#name]` (also accepts `socks://` and `socks5h://`).
         * Returns null if [text] is not a SOCKS URI.
         */
        fun fromUri(text: String, fallbackName: String): Profile? {
            val uri = Uri.parse(text.trim())
            if (uri.scheme?.lowercase() !in setOf("socks", "socks5", "socks5h")) return null
            val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.takeIf { it.isNotEmpty() } ?: return null
            val userInfo = uri.encodedUserInfo
            val user = userInfo?.substringBefore(':')?.let(Uri::decode).orEmpty()
            val pass = userInfo?.takeIf { ':' in it }?.substringAfter(':')?.let(Uri::decode).orEmpty()
            return Profile(
                name = uri.fragment?.takeIf { it.isNotBlank() } ?: fallbackName,
                server = host,
                port = uri.port.takeIf { it in 1..65535 } ?: 1080,
                useAuth = user.isNotEmpty(),
                username = user,
                password = pass,
            )
        }
    }
}
