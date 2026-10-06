package net.typeblog.socks.vpn

import net.typeblog.socks.data.Profile

sealed interface ProxyTestResult {
    /** [latencyMs]: time to complete TCP connect + SOCKS5 greeting (+ auth). [exitIp] may be null if lookup failed. */
    data class Success(val latencyMs: Long, val exitIp: String?) : ProxyTestResult
    data class Failure(val message: String) : ProxyTestResult
}

/** Checks a profile's server without starting the VPN. */
object ProxyTester {
    suspend fun test(profile: Profile): ProxyTestResult = TODO("implemented by the service layer")
}
