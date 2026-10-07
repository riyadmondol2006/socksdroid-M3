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
