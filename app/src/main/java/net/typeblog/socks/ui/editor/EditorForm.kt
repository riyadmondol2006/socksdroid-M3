package net.typeblog.socks.ui.editor

import androidx.compose.runtime.Immutable
import net.typeblog.socks.data.Profile
import net.typeblog.socks.data.RouteMode

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
        server = !isHost(server.trim().removeSurrounding("[", "]")),
        port = port.toPortOrNull() == null,
        username = useAuth && username.isEmpty(),
        // pdnsd needs an IP literal; through the proxy a hostname works too.
        dns = if (remoteDns) !isHost(dns.trim()) else !isIpLiteral(dns.trim()),
        dnsPort = dnsPort.toPortOrNull() == null,
        udpGateway = udp && !isHostPort(udpGateway.trim()),
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

    companion object {
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

private fun String.toPortOrNull(): Int? = trim().toIntOrNull()?.takeIf { it in 1..65535 }

private fun isHost(value: String): Boolean = value.isNotEmpty() && value.none { it.isWhitespace() || it == '/' }

private val IPV4_LITERAL = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")

private fun isIpLiteral(value: String): Boolean =
    IPV4_LITERAL.matches(value) || (':' in value && value.all { it.isLetterOrDigit() || it == ':' || it == '.' })

private val HOST_PORT = Regex("""^(\[[0-9A-Fa-f:.]+]|[^\s:/\[\]]+):(\d{1,5})$""")

/** `host:port` or `[ipv6]:port`, as expected by tun2socks' --udpgw-remote-server-addr. */
private fun isHostPort(value: String): Boolean =
    HOST_PORT.matchEntire(value)?.groupValues?.get(2)?.toPortOrNull() != null
