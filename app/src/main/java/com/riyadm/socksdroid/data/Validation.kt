package com.riyadm.socksdroid.data

/** Field checks shared by the profile editor, imports and the VPN service. */
object Validation {
    private val IPV4 = Regex("""^((25[0-5]|2[0-4]\d|1?\d?\d)\.){3}(25[0-5]|2[0-4]\d|1?\d?\d)$""")
    private val HOST_PORT = Regex("""^(\[[0-9A-Fa-f:.]+]|[^\s:/\[\]]+):(\d{1,5})$""")
    private val FORBIDDEN_HOST_CHARS = charArrayOf('/', ';', '{', '}', '"', '#')

    fun isPort(port: Int): Boolean = port in 1..65535

    fun parsePort(text: String): Int? = text.trim().toIntOrNull()?.takeIf(::isPort)

    fun isIpv4(value: String): Boolean = IPV4.matches(value)

    /** A hostname or IP literal (IPv6 without brackets). */
    fun isHost(value: String): Boolean =
        value.isNotEmpty() && value.none { it.isWhitespace() || it in FORBIDDEN_HOST_CHARS }

    private val LOCAL_IPV4 = Regex("""^(10\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.)""")
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal")

    /**
     * Whether [host] is on the local network: a private or link-local address, or a name that only
     * resolves locally (mDNS `.local`, `.lan`, `.home.arpa`, single-label names). Loopback is excluded.
     */
    fun isLocalNetworkHost(host: String): Boolean {
        val h = host.lowercase().removeSurrounding("[", "]")
        return when {
            isIpv4(h) -> LOCAL_IPV4.containsMatchIn(h)
            ':' in h -> h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") ||
                h.startsWith("fc") || h.startsWith("fd")
            else -> h != "localhost" && ('.' !in h || LOCAL_SUFFIXES.any { h.endsWith(it) })
        }
    }

    /** `host:port` or `[ipv6]:port`, as expected by tun2socks' --udpgw-remote-server-addr. */
    fun isHostPort(value: String): Boolean =
        HOST_PORT.matchEntire(value)?.groupValues?.get(2)?.let(::parsePort) != null
}

/**
 * True if every field can be handed to tun2socks and pdnsd. Without remote DNS, pdnsd needs an
 * IPv4 address (it is built without IPv6); through the proxy a hostname works too.
 */
val Profile.isValid: Boolean
    get() = Validation.isHost(server) && Validation.isPort(port) &&
        (!useAuth || username.isNotEmpty()) &&
        (if (remoteDns) Validation.isHost(dns) else Validation.isIpv4(dns)) && Validation.isPort(dnsPort) &&
        (!udp || Validation.isHostPort(udpGateway))
