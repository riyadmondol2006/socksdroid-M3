package net.typeblog.socks.vpn

import net.typeblog.socks.data.Profile
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Loopback TCP relay that tunnels pdnsd's upstream DNS queries through the SOCKS5 server.
 *
 * pdnsd runs under the app's uid, which is excluded from the VPN, so without this relay its
 * queries would bypass the proxy. pdnsd is pointed at `127.0.0.1:`[port] instead; every accepted
 * connection is forwarded to the profile's DNS server via a SOCKS5 CONNECT.
 */
internal class DnsRelay(private val profile: Profile) : Closeable {
    // Explicit IPv4 loopback: on Android getLoopbackAddress() is ::1, which pdnsd can't reach as 127.0.0.1.
    private val server = ServerSocket(0, BACKLOG, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))

    val port: Int get() = server.localPort

    init {
        thread(name = "dns-relay", isDaemon = true) {
            while (!server.isClosed) {
                val client = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                thread(name = "dns-relay-conn", isDaemon = true) { relay(client) }
            }
        }
    }

    private fun relay(client: Socket) {
        val upstream = Socket()
        try {
            client.use {
                upstream.use {
                    upstream.connect(InetSocketAddress(profile.server.trim(), profile.port), TIMEOUT_MS)
                    upstream.soTimeout = TIMEOUT_MS
                    val input = DataInputStream(upstream.getInputStream())
                    val output = upstream.getOutputStream()
                    Socks5.negotiate(profile, input, output)
                    if (!Socks5.connect(input, output, profile.dns.trim(), profile.dnsPort)) {
                        LogBuffer.append(TAG, "Proxy refused connection to ${profile.dns}:${profile.dnsPort}")
                        return
                    }
                    upstream.soTimeout = 0
                    val toUpstream = thread(name = "dns-relay-up", isDaemon = true) {
                        pump(client.getInputStream(), output)
                        runCatching { upstream.shutdownOutput() }
                    }
                    pump(input, client.getOutputStream())
                    toUpstream.join(TIMEOUT_MS.toLong())
                }
            }
        } catch (e: IOException) {
            if (!server.isClosed) LogBuffer.append(TAG, "DNS relay error: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    private fun pump(from: InputStream, to: OutputStream) {
        try {
            from.copyTo(to)
            to.flush()
        } catch (_: IOException) {
            // Either side closed the connection.
        }
    }

    override fun close() {
        runCatching { server.close() }
    }

    private companion object {
        const val TAG = "dns"
        const val BACKLOG = 16
        const val TIMEOUT_MS = 10_000
    }
}
