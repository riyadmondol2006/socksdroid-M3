package net.typeblog.socks.vpn

import android.os.SystemClock
import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.typeblog.socks.R
import net.typeblog.socks.SocksApp
import net.typeblog.socks.data.Profile
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

    private const val SOCKS_VERSION = 5
    private const val METHOD_NONE = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val METHOD_REJECTED = 0xFF
    private const val CMD_CONNECT = 0x01
    private const val ATYP_IPV4 = 0x01
    private const val ATYP_DOMAIN = 0x03
    private const val ATYP_IPV6 = 0x04

    private val IPV4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    private val IPV6 = Regex("""[0-9a-fA-F]*:[0-9a-fA-F:.]*""")

    private class TestFailure(@StringRes val messageRes: Int) : Exception()

    suspend fun test(profile: Profile): ProxyTestResult = withContext(Dispatchers.IO) {
        val app = SocksApp.instance
        try {
            Socket().use { socket ->
                val started = SystemClock.elapsedRealtime()
                socket.connect(InetSocketAddress(profile.server.trim(), profile.port), TIMEOUT_MS)
                socket.soTimeout = TIMEOUT_MS
                val input = DataInputStream(socket.getInputStream().buffered())
                val output = socket.getOutputStream()
                negotiate(profile, input, output)
                val latency = SystemClock.elapsedRealtime() - started
                val exitIp = try {
                    lookupExitIp(input, output)
                } catch (_: IOException) {
                    null
                }
                ProxyTestResult.Success(latency, exitIp)
            }
        } catch (e: TestFailure) {
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

    /** SOCKS5 method negotiation (RFC 1928) and, if requested, username/password auth (RFC 1929). */
    private fun negotiate(profile: Profile, input: DataInputStream, output: OutputStream) {
        val methods = if (profile.useAuth) listOf(METHOD_NONE, METHOD_USER_PASS) else listOf(METHOD_NONE)
        output.write(byteArrayOf(SOCKS_VERSION.toByte(), methods.size.toByte()) + methods.map(Int::toByte))
        output.flush()
        if (input.readUnsignedByte() != SOCKS_VERSION) throw TestFailure(R.string.vpn_test_not_socks5)
        when (input.readUnsignedByte()) {
            METHOD_NONE -> Unit
            METHOD_USER_PASS ->
                if (profile.useAuth) authenticate(profile, input, output) else throw TestFailure(R.string.vpn_test_auth_required)
            METHOD_REJECTED ->
                throw TestFailure(if (profile.useAuth) R.string.vpn_test_no_method else R.string.vpn_test_auth_required)
            else -> throw TestFailure(R.string.vpn_test_no_method)
        }
    }

    private fun authenticate(profile: Profile, input: DataInputStream, output: OutputStream) {
        val user = profile.username.toByteArray()
        val pass = profile.password.toByteArray()
        if (user.size > 255 || pass.size > 255) throw TestFailure(R.string.vpn_test_credentials_too_long)
        output.write(byteArrayOf(0x01, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
        output.flush()
        input.readUnsignedByte() // sub-negotiation version; some servers wrongly send 5
        if (input.readUnsignedByte() != 0) throw TestFailure(R.string.vpn_test_auth_failed)
    }

    /** Opens a CONNECT tunnel to [IP_HOST] and returns the address it reports, or null. */
    private fun lookupExitIp(input: DataInputStream, output: OutputStream): String? {
        val host = IP_HOST.toByteArray()
        output.write(
            byteArrayOf(SOCKS_VERSION.toByte(), CMD_CONNECT.toByte(), 0, ATYP_DOMAIN.toByte(), host.size.toByte()) +
                host + byteArrayOf((IP_PORT shr 8).toByte(), IP_PORT.toByte()),
        )
        output.flush()
        input.readUnsignedByte()
        if (input.readUnsignedByte() != 0) return null
        input.readUnsignedByte()
        val boundAddressLength = when (input.readUnsignedByte()) {
            ATYP_IPV4 -> 4
            ATYP_IPV6 -> 16
            ATYP_DOMAIN -> input.readUnsignedByte()
            else -> return null
        }
        input.skipFully(boundAddressLength + 2)

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

    private fun DataInputStream.skipFully(count: Int) = readFully(ByteArray(count))

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
