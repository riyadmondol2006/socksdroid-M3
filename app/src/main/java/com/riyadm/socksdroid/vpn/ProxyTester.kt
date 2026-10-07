package com.riyadm.socksdroid.vpn

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.SocksApp
import com.riyadm.socksdroid.data.Profile
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException

sealed interface ProxyTestResult {
    /** [latencyMs]: time to complete TCP connect + SOCKS5 greeting (+ auth). [exitIp] may be null if lookup failed. */
    data class Success(val latencyMs: Long, val exitIp: String?) : ProxyTestResult
    data class Failure(val message: String) : ProxyTestResult
}

/**
 * Checks a profile's server without starting the VPN. The app is excluded from its own VPN, so
 * the test always talks to the server directly.
 */
object ProxyTester {
    private const val TIMEOUT_MS = 5_000
    private const val IP_HOST = "api.ipify.org"
    private const val IP_PORT = 80
    private const val MAX_RESPONSE_BYTES = 16 * 1024

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val IPV6 = Regex("""[0-9a-fA-F]*:[0-9a-fA-F:.]*""")

    suspend fun test(profile: Profile): ProxyTestResult = withContext(Dispatchers.IO) {
        val app = SocksApp.instance
        try {
            Socket().use { socket ->
                val started = SystemClock.elapsedRealtime()
                socket.connect(InetSocketAddress(profile.server.trim(), profile.port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val input = DataInputStream(socket.getInputStream().buffered())
                val output = socket.getOutputStream()
                Socks5.negotiate(profile, input, output)
                val latency = SystemClock.elapsedRealtime() - started
                val exitIp = try {
                    lookupExitIp(input, output)
                } catch (_: IOException) {
                    null
                }
                ProxyTestResult.Success(latency, exitIp)
            }
        } catch (e: Socks5.Failure) {
            ProxyTestResult.Failure(app.getString(e.messageRes))
        } catch (e: IOException) {
            ProxyTestResult.Failure(describe(e))
        } catch (_: IllegalArgumentException) {
            ProxyTestResult.Failure(app.getString(R.string.vpn_test_invalid_port))
        }
    }

    private fun describe(e: IOException): String {
        val app = SocksApp.instance
        return when (e) {
            is SocketTimeoutException -> app.getString(R.string.vpn_test_timeout)
            is UnknownHostException -> app.getString(R.string.vpn_test_unknown_host)
            is NoRouteToHostException -> app.getString(R.string.vpn_test_unreachable)
            is ConnectException ->
                if (e.message?.contains("ECONNREFUSED") == true || e.message?.contains("refused") == true) {
                    app.getString(R.string.vpn_test_refused)
                } else {
                    app.getString(R.string.vpn_test_unreachable)
                }
            is EOFException -> app.getString(R.string.vpn_test_closed)
            else -> e.message ?: e.javaClass.simpleName
        }
    }

    /** Opens a CONNECT tunnel to [IP_HOST] and returns the address it reports, or null. */
    private fun lookupExitIp(input: DataInputStream, output: OutputStream): String? {
        if (!Socks5.connect(input, output, IP_HOST, IP_PORT)) return null
        output.write("GET / HTTP/1.1\r\nHost: $IP_HOST\r\nConnection: close\r\n\r\n".toByteArray())
        output.flush()
        val response = input.readAtMost(MAX_RESPONSE_BYTES).decodeToString()
        val head = response.substringBefore("\r\n\r\n")
        if (head.lineSequence().firstOrNull()?.split(' ')?.getOrNull(1) != "200") return null
        // Works for both plain and chunked bodies: pick the line that looks like an address.
        return response.substringAfter("\r\n\r\n").lineSequence()
            .map(String::trim)
            .firstOrNull { IPV4.matches(it) || IPV6.matches(it) }
    }

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
