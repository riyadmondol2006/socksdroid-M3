package com.riyadm.socksdroid.vpn

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.riyadm.socksdroid.R
import com.riyadm.socksdroid.SocksApp
import com.riyadm.socksdroid.data.Profile
import com.riyadm.socksdroid.data.isValid
import java.io.File
import java.io.FileDescriptor
import java.io.IOException
import kotlin.concurrent.thread

internal const val LOG_TAG = "vpn"

/**
 * Runs the tunnel: establishes the tun interface, then supervises pdnsd (DNS over TCP) and
 * tun2socks (tun <-> SOCKS5). Runs in the main app process and publishes its state only through
 * [VpnStateHolder]. Main-thread confined; blocking work runs on [Dispatchers.IO].
 */
class SocksVpnService : VpnService() {
    private val scope = MainScope()
    private lateinit var notifications: VpnNotifications
    private lateinit var pdnsd: NativeDaemon
    private lateinit var tun2socks: NativeDaemon
    private val sockFile by lazy { File(filesDir, "sock_path") }

    private var session: Job? = null
    private var sessionProfile: Profile? = null
    private var stopJob: Job? = null
    private var lastStartId = 0

    /** Serializes sessions: a new one only connects after the previous one has fully released. */
    private val sessionLock = Mutex()

    @Volatile
    private var tun: ParcelFileDescriptor? = null

    @Volatile
    private var dnsRelay: DnsRelay? = null

    private class SessionFailure(message: String) : Exception(message)

    override fun onCreate() {
        super.onCreate()
        notifications = VpnNotifications(this).also { it.createChannel() }
        val libDir = File(applicationInfo.nativeLibraryDir)
        pdnsd = NativeDaemon("pdnsd", File(libDir, "libpdnsd.so"), File(filesDir, "pdnsd.pid"))
        tun2socks = NativeDaemon("tun2socks", File(libDir, "libtun2socks.so"), File(filesDir, "tun2socks.pid"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_STOP -> {
                disconnect()
                return START_NOT_STICKY
            }
            // Reconnect with the active profile, which may have been edited or switched.
            ACTION_RESTART -> if (session?.isActive == true) {
                start(resolveProfile(null), force = true)
            } else if (stopJob?.isActive != true) {
                shutdown()
            }
            // ACTION_START, SERVICE_INTERFACE (always-on VPN) and sticky restarts (null intent).
            else -> start(resolveProfile(intent?.getStringExtra(EXTRA_PROFILE)), force = false)
        }
        return START_STICKY
    }

    /** Another VPN took over or the user revoked consent. */
    override fun onRevoke() {
        LogBuffer.append(LOG_TAG, "VPN permission revoked")
        disconnect()
    }

    override fun onDestroy() {
        scope.cancel()
        // Stopping the daemons can block for a moment; keep it off the main thread.
        thread(name = "vpn-teardown") { releaseResources() }
        if (VpnStateHolder.state.value !is VpnState.Error) setState(VpnState.Disconnected)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun resolveProfile(name: String?): Profile {
        val profiles = SocksApp.instance.profiles
        return name?.let(profiles::get) ?: profiles.active
    }

    /** Connects with [profile]; a running session with identical settings is kept unless [force]. */
    private fun start(profile: Profile, force: Boolean) {
        val unchanged = !force && session?.isActive == true && sessionProfile == profile
        val connectedSince = (VpnStateHolder.state.value as? VpnState.Connected)?.since?.takeIf { unchanged }
        // Must run for every start: startForegroundService() requires a startForeground() call.
        if (!enterForeground(profile, connectedSince)) return
        stopJob?.cancel()
        stopJob = null
        if (!unchanged) launchSession(profile)
    }

    private fun disconnect() {
        val running = session
        if (running == null || !running.isActive) {
            shutdown()
            return
        }
        if (stopJob?.isActive == true) return
        setState(VpnState.Disconnecting)
        LogBuffer.append(LOG_TAG, "Disconnecting")
        stopJob = scope.launch {
            running.cancelAndJoin()
            setState(VpnState.Disconnected)
            shutdown()
        }
    }

    /**
     * Stops the service unless a newer start command is already queued, in which case that command
     * must still be able to call startForeground().
     */
    private fun shutdown() {
        if (stopSelfResult(lastStartId)) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun launchSession(profile: Profile) {
        // Cancel synchronously so a session replaced before it even started can't outlive this one.
        session?.cancel()
        sessionProfile = profile
        session = scope.launch {
            sessionLock.withLock {
                var failure: String? = null
                try {
                    connect(profile)
                    supervise(profile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SessionFailure) {
                    failure = e.message
                } catch (e: Exception) {
                    failure = getString(R.string.vpn_error_unexpected, e.message ?: e.javaClass.simpleName)
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) { releaseResources() }
                }
                // A session that was replaced or stopped meanwhile must not touch the service.
                if (failure != null && isActive) {
                    LogBuffer.append(LOG_TAG, "Error: $failure")
                    setState(VpnState.Error(failure))
                    shutdown()
                }
            }
        }
    }

    private suspend fun connect(profile: Profile) {
        setState(VpnState.Connecting(profile.name))
        if (!profile.isValid) fail(R.string.vpn_error_invalid_profile)
        if (LocalNetwork.isMissingFor(this, profile)) fail(R.string.vpn_error_local_network)
        LogBuffer.append(LOG_TAG, "Connecting to ${profile.endpoint} (profile \"${profile.name}\")")
        withContext(Dispatchers.IO) {
            pdnsd.killStale()
            tun2socks.killStale()
            val fd = establish(profile)
            tun = fd
            if (profile.remoteDns) dnsRelay = DnsRelay(profile)
            startPdnsd(profile)
            if (!startTun2socks(profile, fd.fileDescriptor)) fail(R.string.vpn_error_tun2socks_start)
        }
        setConnected(profile)
    }

    private fun establish(profile: Profile): ParcelFileDescriptor {
        val fd = try {
            Builder()
                .configure(this, profile)
                .setConfigureIntent(VpnNotifications.openAppIntent(this))
                .establish()
        } catch (e: RuntimeException) {
            LogBuffer.append(LOG_TAG, "establish() failed: $e")
            fail(R.string.vpn_error_establish)
        }
        return fd ?: fail(R.string.vpn_error_permission)
    }

    /** Keeps both daemons alive for the lifetime of the session, restarting them if allowed. */
    private suspend fun supervise(profile: Profile) = coroutineScope {
        launch {
            supervise(pdnsd, R.string.vpn_error_pdnsd_stopped) {
                withContext(Dispatchers.IO) { startPdnsd(profile) }
            }
        }
        launch {
            supervise(tun2socks, R.string.vpn_error_tun2socks_stopped) {
                setState(VpnState.Connecting(profile.name))
                enterForeground(profile, connectedSince = null)
                val fd = checkNotNull(tun).fileDescriptor
                if (withContext(Dispatchers.IO) { startTun2socks(profile, fd) }) setConnected(profile)
            }
        }
    }

    /**
     * Waits for [daemon] to exit and restarts it with [restart] if auto-reconnect is enabled, with
     * exponential backoff. Gives up after [MAX_RESTARTS] consecutive failures; a daemon that
     * stayed up for [STABLE_RUN_MS] resets the count.
     */
    private suspend fun supervise(daemon: NativeDaemon, @StringRes failure: Int, restart: suspend () -> Unit) {
        var failures = 0
        while (true) {
            val startedAt = SystemClock.elapsedRealtime()
            val code = daemon.awaitExit()
            LogBuffer.append(LOG_TAG, "${daemon.tag} exited unexpectedly (code $code)")
            withContext(Dispatchers.IO) { daemon.stop() }
            if (SystemClock.elapsedRealtime() - startedAt > STABLE_RUN_MS) failures = 0
            if (!SocksApp.instance.settings.settings.value.autoReconnect || failures >= MAX_RESTARTS) {
                fail(failure)
            }
            delay(RESTART_BACKOFF_MS shl failures)
            failures++
            LogBuffer.append(LOG_TAG, "Restarting ${daemon.tag} (attempt $failures of $MAX_RESTARTS)")
            restart()
        }
    }

    private fun startPdnsd(profile: Profile) {
        val config = File(filesDir, "pdnsd.conf")
        // With remote DNS, pdnsd queries the loopback relay, which tunnels through the proxy.
        val relay = dnsRelay
        config.writeText(
            pdnsdConfig(
                cacheDir = filesDir.path,
                upstreamIp = if (relay != null) "127.0.0.1" else profile.dns.trim(),
                upstreamPort = relay?.port ?: profile.dnsPort,
            ),
        )
        File(filesDir, "pdnsd.cache").createNewFile()
        try {
            pdnsd.start(listOf("-c", config.path), filesDir)
        } catch (e: IOException) {
            LogBuffer.append(LOG_TAG, "Could not start pdnsd: ${e.message}")
            fail(R.string.vpn_error_pdnsd_start)
        }
    }

    /** Starts tun2socks and hands it the tun [fd]. Returns false (with tun2socks stopped) on failure. */
    private suspend fun startTun2socks(profile: Profile, fd: FileDescriptor): Boolean {
        sockFile.delete()
        try {
            tun2socks.start(tun2socksArgs(profile), filesDir)
        } catch (e: IOException) {
            LogBuffer.append(LOG_TAG, "Could not start tun2socks: ${e.message}")
            return false
        }
        if (sendFd(fd)) return true
        tun2socks.stop()
        return false
    }

    private fun tun2socksArgs(profile: Profile): List<String> = buildList {
        add("--netif-ipaddr"); add(TunConfig.GATEWAY)
        add("--netif-netmask"); add(TunConfig.NETMASK)
        add("--socks-server-addr"); add(profile.endpoint)
        add("--tunmtu"); add(TunConfig.MTU.toString())
        add("--loglevel"); add("3")
        add("--sock"); add(sockFile.path)
        add("--dnsgw"); add("${TunConfig.ADDRESS}:${TunConfig.DNS_PORT}")
        if (profile.useAuth && profile.username.isNotEmpty()) {
            add("--username"); add(profile.username)
            add("--password"); add(profile.password)
        }
        if (profile.ipv6) {
            add("--netif-ip6addr"); add(TunConfig.GATEWAY6)
        }
        if (profile.udp && profile.udpGateway.isNotBlank()) {
            add("--udpgw-remote-server-addr"); add(profile.udpGateway.trim())
        }
    }

    /**
     * Passes the tun fd to tun2socks over its unix socket (SCM_RIGHTS), retrying with backoff
     * while tun2socks starts listening.
     */
    private suspend fun sendFd(fd: FileDescriptor): Boolean {
        val deadline = SystemClock.elapsedRealtime() + FD_SEND_TIMEOUT_MS
        var wait = 50L
        while (true) {
            if (!tun2socks.isRunning) {
                LogBuffer.append(LOG_TAG, "tun2socks exited during startup")
                return false
            }
            try {
                LocalSocket().use { socket ->
                    socket.connect(LocalSocketAddress(sockFile.path, LocalSocketAddress.Namespace.FILESYSTEM))
                    socket.setFileDescriptorsForSend(arrayOf(fd))
                    socket.outputStream.write(42)
                }
                return true
            } catch (e: IOException) {
                if (SystemClock.elapsedRealtime() >= deadline) {
                    LogBuffer.append(LOG_TAG, "Could not pass the tun fd to tun2socks: ${e.message}")
                    return false
                }
            }
            delay(wait)
            wait = (wait * 2).coerceAtMost(500)
        }
    }

    private fun setConnected(profile: Profile) {
        val since = SystemClock.elapsedRealtime()
        setState(VpnState.Connected(profile.name, since))
        enterForeground(profile, since)
        LogBuffer.append(LOG_TAG, "Connected")
    }

    /** Stops both daemons and closes the tun interface. Idempotent and thread-safe. */
    @Synchronized
    private fun releaseResources() {
        tun2socks.stop()
        pdnsd.stop()
        dnsRelay?.close()
        dnsRelay = null
        tun?.let {
            tun = null
            try {
                it.close()
            } catch (e: IOException) {
                LogBuffer.append(LOG_TAG, "Closing tun failed: ${e.message}")
            }
        }
        sockFile.delete()
    }

    /**
     * Posts (or updates) the foreground notification. Returns false if the system refused it.
     * ServiceCompat drops the API 34 service type on older releases.
     */
    @SuppressLint("InlinedApi")
    private fun enterForeground(profile: Profile, connectedSince: Long?): Boolean = try {
        ServiceCompat.startForeground(
            this,
            VpnNotifications.NOTIFICATION_ID,
            notifications.build(profile, connectedSince),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException, e.g. a sticky restart from the background.
        LogBuffer.append(LOG_TAG, "Could not start in foreground: ${e.message}")
        setState(VpnState.Error(getString(R.string.vpn_error_foreground)))
        shutdown()
        false
    }

    private fun setState(state: VpnState) {
        VpnStateHolder.set(state)
        try {
            TileService.requestListeningState(this, ComponentName(this, ProxyTileService::class.java))
        } catch (_: RuntimeException) {
            // Some builds throw if the tile is not added; the tile then refreshes when shown.
        }
    }

    private fun fail(@StringRes message: Int): Nothing = throw SessionFailure(getString(message))

    companion object {
        const val ACTION_START = "com.riyadm.socksdroid.action.START"
        const val ACTION_STOP = "com.riyadm.socksdroid.action.STOP"
        const val ACTION_RESTART = "com.riyadm.socksdroid.action.RESTART"

        /** Optional profile name for [ACTION_START]; the active profile is used when absent. */
        const val EXTRA_PROFILE = "com.riyadm.socksdroid.extra.PROFILE"

        private const val FD_SEND_TIMEOUT_MS = 5_000L
        private const val MAX_RESTARTS = 3
        private const val RESTART_BACKOFF_MS = 1_000L
        private const val STABLE_RUN_MS = 60_000L

        fun intent(context: Context, action: String): Intent =
            Intent(context, SocksVpnService::class.java).setAction(action)
    }
}
