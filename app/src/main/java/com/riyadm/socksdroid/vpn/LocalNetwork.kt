package com.riyadm.socksdroid.vpn

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.data.Validation

/**
 * Android 17 (API 37) blocks LAN connections of apps targeting it unless they hold the
 * ACCESS_LOCAL_NETWORK runtime permission. tun2socks and the DNS relay run under this app's uid,
 * so a SOCKS server (or direct DNS server) on the local network needs it.
 */
object LocalNetwork {
    const val PERMISSION = Manifest.permission.ACCESS_LOCAL_NETWORK

    val isRequired: Boolean get() = Build.VERSION.SDK_INT >= 37

    fun isGranted(context: Context): Boolean =
        !isRequired || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** True if connecting with [profile] needs the permission and it hasn't been granted. */
    fun isMissingFor(context: Context, profile: Profile): Boolean =
        isRequired && profile.usesLocalNetwork && !isGranted(context)
}

/** Whether the proxy server, or the DNS server when queried directly, is on the local network. */
val Profile.usesLocalNetwork: Boolean
    get() = Validation.isLocalNetworkHost(server.trim()) || (!remoteDns && Validation.isLocalNetworkHost(dns.trim()))
