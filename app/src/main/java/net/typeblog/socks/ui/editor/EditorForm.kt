package net.typeblog.socks.ui.editor

import androidx.compose.runtime.Immutable
import net.typeblog.socks.data.Profile
import net.typeblog.socks.data.RouteMode
import net.typeblog.socks.data.Validation
import org.json.JSONArray
import org.json.JSONObject

/** Editable copy of a [Profile]; numeric fields stay as text so partial input can be shown. */
@Immutable
data class EditorForm(
    val server: String,
    val port: String,
    val useAuth: Boolean,
    val username: String,
    val password: String,
    val route: RouteMode,
    val bypassLan: Boolean,
    val dns: String,
    val dnsPort: String,
    val remoteDns: Boolean,
    val ipv6: Boolean,
    val udp: Boolean,
    val udpGateway: String,
    val perApp: Boolean,
    val bypassApps: Boolean,
    val apps: Set<String>,
) {
    fun validate() = EditorErrors(
        server = !Validation.isHost(server.trim().removeSurrounding("[", "]")),
        port = port.toPortOrNull() == null,
        username = useAuth && username.isEmpty(),
        // pdnsd needs an IPv4 literal; through the proxy a hostname works too.
        dns = if (remoteDns) !Validation.isHost(dns.trim()) else !Validation.isIpv4(dns.trim()),
        dnsPort = dnsPort.toPortOrNull() == null,
        udpGateway = udp && !Validation.isHostPort(udpGateway.trim()),
    )

    /** The profile this form describes, or null if any field is invalid. */
    fun toProfile(name: String): Profile? {
        if (validate().any) return null
        return Profile(
            name = name,
            server = server.trim().removeSurrounding("[", "]"),
            port = port.toPortOrNull()!!,
            useAuth = useAuth,
            username = username,
            password = password,
            route = route,
            bypassLan = bypassLan,
            dns = dns.trim(),
            dnsPort = dnsPort.toPortOrNull()!!,
            remoteDns = remoteDns,
            perApp = perApp,
            bypassApps = bypassApps,
            apps = apps,
            ipv6 = ipv6,
            udp = udp,
            udpGateway = udpGateway.trim(),
        )
    }

    fun toJson(): String = JSONObject()
        .put("server", server).put("port", port)
        .put("useAuth", useAuth).put("username", username).put("password", password)
        .put("route", route.key).put("bypassLan", bypassLan)
        .put("dns", dns).put("dnsPort", dnsPort).put("remoteDns", remoteDns)
        .put("ipv6", ipv6).put("udp", udp).put("udpGateway", udpGateway)
        .put("perApp", perApp).put("bypassApps", bypassApps).put("apps", JSONArray(apps.toList()))
        .toString()

    companion object {
        fun fromJson(json: String): EditorForm? = runCatching {
            val o = JSONObject(json)
            val apps = o.getJSONArray("apps")
            EditorForm(
                server = o.getString("server"),
                port = o.getString("port"),
                useAuth = o.getBoolean("useAuth"),
                username = o.getString("username"),
                password = o.getString("password"),
                route = RouteMode.fromKey(o.getString("route")),
                bypassLan = o.getBoolean("bypassLan"),
                dns = o.getString("dns"),
                dnsPort = o.getString("dnsPort"),
                remoteDns = o.getBoolean("remoteDns"),
                ipv6 = o.getBoolean("ipv6"),
                udp = o.getBoolean("udp"),
                udpGateway = o.getString("udpGateway"),
                perApp = o.getBoolean("perApp"),
                bypassApps = o.getBoolean("bypassApps"),
                apps = (0 until apps.length()).map(apps::getString).toSet(),
            )
        }.getOrNull()

        fun from(p: Profile) = EditorForm(
            server = p.server,
            port = p.port.toString(),
            useAuth = p.useAuth,
            username = p.username,
            password = p.password,
            route = p.route,
            bypassLan = p.bypassLan,
            dns = p.dns,
            dnsPort = p.dnsPort.toString(),
            remoteDns = p.remoteDns,
            ipv6 = p.ipv6,
            udp = p.udp,
            udpGateway = p.udpGateway,
            perApp = p.perApp,
            bypassApps = p.bypassApps,
            apps = p.apps,
        )
    }
}

@Immutable
data class EditorErrors(
    val server: Boolean = false,
    val port: Boolean = false,
    val username: Boolean = false,
    val dns: Boolean = false,
    val dnsPort: Boolean = false,
    val udpGateway: Boolean = false,
) {
    val any: Boolean get() = server || port || username || dns || dnsPort || udpGateway
}

private fun String.toPortOrNull(): Int? = Validation.parsePort(this)
