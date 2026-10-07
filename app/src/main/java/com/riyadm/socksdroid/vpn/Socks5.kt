package com.riyadm.socksdroid.vpn

import androidx.annotation.StringRes
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.data.Validation
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress

/** Minimal SOCKS5 client (RFC 1928 / RFC 1929) used by [ProxyTester] and [DnsRelay]. */
internal object Socks5 {
    private const val VERSION = 5
    private const val METHOD_NONE = 0x00
    private const val METHOD_USER_PASS = 0x02
    private const val METHOD_REJECTED = 0xFF
    private const val CMD_CONNECT = 0x01
    private const val ATYP_IPV4 = 0x01
    private const val ATYP_DOMAIN = 0x03
    private const val ATYP_IPV6 = 0x04

    /** A protocol-level failure with a user-facing message. */
    class Failure(@StringRes val messageRes: Int) : IOException()

    /** Method negotiation and, if the profile has credentials, username/password authentication. */
    fun negotiate(profile: Profile, input: DataInputStream, output: OutputStream) {
        // Matches tun2socks, which only authenticates when a username is set.
        val useAuth = profile.useAuth && profile.username.isNotEmpty()
        val methods = if (useAuth) listOf(METHOD_NONE, METHOD_USER_PASS) else listOf(METHOD_NONE)
        output.write(byteArrayOf(VERSION.toByte(), methods.size.toByte()) + methods.map(Int::toByte))
        output.flush()
        if (input.readUnsignedByte() != VERSION) throw Failure(R.string.vpn_test_not_socks5)
        when (input.readUnsignedByte()) {
            METHOD_NONE -> Unit
            METHOD_USER_PASS ->
                if (useAuth) authenticate(profile, input, output) else throw Failure(R.string.vpn_test_auth_required)
            METHOD_REJECTED ->
                throw Failure(if (useAuth) R.string.vpn_test_no_method else R.string.vpn_test_auth_required)
            else -> throw Failure(R.string.vpn_test_no_method)
        }
    }

    private fun authenticate(profile: Profile, input: DataInputStream, output: OutputStream) {
        val user = profile.username.toByteArray()
        val pass = profile.password.toByteArray()
        if (user.size > 255 || pass.size > 255) throw Failure(R.string.vpn_test_credentials_too_long)
        output.write(byteArrayOf(0x01, user.size.toByte()) + user + byteArrayOf(pass.size.toByte()) + pass)
        output.flush()
        input.readUnsignedByte() // sub-negotiation version; some servers wrongly send 5
        if (input.readUnsignedByte() != 0) throw Failure(R.string.vpn_test_auth_failed)
    }

    /**
     * Sends a CONNECT request for [host]:[port] on an authenticated connection and consumes the reply.
     * Returns false if the server refused to open the tunnel.
     */
    fun connect(input: DataInputStream, output: OutputStream, host: String, port: Int): Boolean {
        output.write(byteArrayOf(VERSION.toByte(), CMD_CONNECT.toByte(), 0) + encodeAddress(host) +
            byteArrayOf((port shr 8).toByte(), port.toByte()))
        output.flush()
        input.readUnsignedByte()
        val reply = input.readUnsignedByte()
        input.readUnsignedByte()
        val boundAddressLength = when (input.readUnsignedByte()) {
            ATYP_IPV4 -> 4
            ATYP_IPV6 -> 16
            ATYP_DOMAIN -> input.readUnsignedByte()
            else -> return false
        }
        input.readFully(ByteArray(boundAddressLength + 2))
        return reply == 0
    }

    private fun encodeAddress(host: String): ByteArray = when {
        // Literals are parsed locally; InetAddress never does a DNS lookup for them.
        Validation.isIpv4(host) -> byteArrayOf(ATYP_IPV4.toByte()) + InetAddress.getByName(host).address
        ':' in host -> byteArrayOf(ATYP_IPV6.toByte()) + InetAddress.getByName(host).address
        else -> host.toByteArray().let { byteArrayOf(ATYP_DOMAIN.toByte(), it.size.toByte()) + it }
    }
}
