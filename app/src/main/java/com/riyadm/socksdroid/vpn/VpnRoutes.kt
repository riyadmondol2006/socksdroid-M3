package com.riyadm.socksdroid.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.data.RouteMode

/** Addresses of the tun interface shared by the VPN builder and the native daemons. */
internal object TunConfig {
    const val MTU = 1500
    const val ADDRESS = "26.26.26.1"
    const val PREFIX = 24
    const val NETMASK = "255.255.255.0"

    /** tun2socks' own address on the virtual network. */
    const val GATEWAY = "26.26.26.2"

    const val ADDRESS6 = "fdfe:dcba:9876::1"
    const val PREFIX6 = 126
    const val GATEWAY6 = "fdfe:dcba:9876::2"

    /** DNS server announced to apps; queries to it are routed into the tun and answered by pdnsd. */
    const val DNS_STUB = "8.8.8.8"

    /** pdnsd listen port; tun2socks forwards DNS packets to ADDRESS:DNS_PORT. */
    const val DNS_PORT = 8091
}

internal object VpnRoutes {
    private val DEFAULT_ROUTE = Ipv4Cidr(0, 0)
    private val LOOPBACK = cidr("127.0.0.0/8")

    /** Private, link-local, CGNAT, loopback, multicast and reserved ranges skipped by "bypass LAN". */
    private val LOCAL_NETWORKS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "169.254.0.0/16",
        "100.64.0.0/10", "127.0.0.0/8", "224.0.0.0/4", "240.0.0.0/4",
    ).map(::cidr)

    private fun cidr(text: String) = checkNotNull(Ipv4Cidr.parse(text))

    /** IPv4 networks that should be sent through the proxy for [profile]. */
    fun ipv4Routes(context: Context, profile: Profile): List<Ipv4Cidr> {
        val routes = when (profile.route) {
            RouteMode.ALL -> listOf(DEFAULT_ROUTE)
            RouteMode.NON_CHINA -> context.resources.getStringArray(R.array.simple_route)
                .mapNotNull(Ipv4Cidr::parse)
                .filterNot { it in LOOPBACK }
        }
        return if (profile.bypassLan) routes.excluding(LOCAL_NETWORKS) else routes
    }
}

/** Applies addresses, routes, DNS and per-app rules for [profile]. */
internal fun VpnService.Builder.configure(context: Context, profile: Profile): VpnService.Builder {
    setMtu(TunConfig.MTU)
    setSession(profile.name)
    addAddress(TunConfig.ADDRESS, TunConfig.PREFIX)
    addDnsServer(TunConfig.DNS_STUB)
    VpnRoutes.ipv4Routes(context, profile).forEach { addRoute(it.address, it.prefix) }
    addRoute(TunConfig.DNS_STUB, 32)
    // IPv6 is always captured: tun2socks forwards it when profile.ipv6 is set and drops it
    // otherwise, so IPv6 traffic can never bypass the proxy (apps fall back to IPv4).
    addAddress(TunConfig.ADDRESS6, TunConfig.PREFIX6)
    addRoute("::", 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) setMetered(false)
    applyAppRules(context.packageName, profile)
    return this
}

/**
 * The app itself must never be routed through the VPN: tun2socks and pdnsd run under its uid
 * and have to reach the SOCKS server and upstream DNS directly.
 */
private fun VpnService.Builder.applyAppRules(ownPackage: String, profile: Profile) {
    val listed = profile.apps.map(String::trim).filter { it.isNotEmpty() && it != ownPackage }
    if (profile.perApp && !profile.bypassApps) {
        val allowed = listed.count { tryAddApp(it) { pkg -> addAllowedApplication(pkg) } }
        if (allowed > 0) return
        LogBuffer.append(LOG_TAG, "None of the selected apps is installed; proxying all apps")
    }
    addDisallowedApplication(ownPackage)
    if (profile.perApp && profile.bypassApps) {
        listed.forEach { tryAddApp(it) { pkg -> addDisallowedApplication(pkg) } }
    }
}

private inline fun tryAddApp(pkg: String, add: (String) -> Unit): Boolean = try {
    add(pkg)
    true
} catch (_: PackageManager.NameNotFoundException) {
    LogBuffer.append(LOG_TAG, "App $pkg is not installed, skipping")
    false
}
