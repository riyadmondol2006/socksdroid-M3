package net.typeblog.socks.vpn

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A native executable shipped in `nativeLibraryDir`, run in the foreground as a child process.
 * Its combined stdout/stderr is forwarded line by line to [LogBuffer] under [tag].
 *
 * The process is started through `sh`, which records its pid in [pidFile] and then `exec`s the
 * binary in place (keeping the same pid). This lets a later app process find and kill daemons
 * orphaned by a crash, since `Process.pid()` is not available on Android.
 */
internal class NativeDaemon(
    val tag: String,
    private val executable: File,
    private val pidFile: File,
) {
    @Volatile
    private var process: Process? = null

    val isRunning: Boolean get() = process?.isAlive == true

    /** Starts the daemon with [args]. Blocking; call from a background thread. */
    @Synchronized
    fun start(args: List<String>, workDir: File) {
        check(process == null) { "$tag is already running" }
        pidFile.delete()
        val command = listOf("/system/bin/sh", "-c", LAUNCHER, pidFile.path, executable.path) + args
        val started = ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        started.outputStream.close()
        process = started
        thread(name = "$tag-output", isDaemon = true) {
            try {
                started.inputStream.bufferedReader().forEachLine { LogBuffer.append(tag, it) }
            } catch (_: IOException) {
                // The pipe is closed when the process is destroyed.
            }
        }
    }

    /** Suspends until the current process exits and returns its exit code (-1 if none is running). */
    suspend fun awaitExit(): Int {
        val current = process ?: return -1
        return runInterruptible(Dispatchers.IO) { current.waitFor() }
    }

    /** Terminates the daemon (SIGTERM, then SIGKILL after a grace period). Blocking; idempotent. */
    @Synchronized
    fun stop() {
        val current = process ?: return
        process = null
        current.destroy()
        if (!current.waitFor(STOP_GRACE_MS, TimeUnit.MILLISECONDS)) current.destroyForcibly()
        pidFile.delete()
    }

    /**
     * Kills a daemon left behind by a previous app process, identified by [pidFile].
     * The pid is only signalled if its command line still names our executable, so a recycled
     * pid can never hit an unrelated process.
     */
    fun killStale() {
        if (process != null) return
        val pid = runCatching { pidFile.readText().trim().toInt() }.getOrNull()
        pidFile.delete()
        if (pid == null || pid <= 0) return
        val cmdline = runCatching { File("/proc/$pid/cmdline").readText() }.getOrNull() ?: return
        if (executable.name !in cmdline) return
        try {
            Os.kill(pid, OsConstants.SIGKILL)
            LogBuffer.append(LOG_TAG, "Killed stale $tag process $pid")
        } catch (e: ErrnoException) {
            LogBuffer.append(LOG_TAG, "Could not kill stale $tag process $pid: ${e.message}")
        }
    }

    private companion object {
        const val STOP_GRACE_MS = 1_000L

        /** `$0` is the pid file, `$@` the executable and its arguments. */
        const val LAUNCHER = "echo \$\$ > \"\$0\" && exec \"\$@\""
    }
}
