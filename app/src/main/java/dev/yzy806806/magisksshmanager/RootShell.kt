package dev.yzy806806.magisksshmanager

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

/**
 * Root shell helper: runs commands via Magisk su and returns combined output.
 * All module management goes through this single class so future features
 * (e.g. managing other daemons like meshdesk) just call [exec].
 */
object RootShell {

    private const val TAG = "RootShell"

    // su binary lives at different paths on different devices (Magisk usually links
    // /system/bin/su, but some ROMs keep it at /product/bin/su / system/xbin/su)
    private val suPaths = listOf("/system/bin/su", "/product/bin/su", "/system/xbin/su", "/sbin/su", "su")

    private fun suBinary(): String? =
        suPaths.firstOrNull { path ->
            try {
                val p = Runtime.getRuntime().exec(arrayOf(path))
                p.outputStream.bufferedWriter().use { it.write("id\n"); it.flush() }
                val out = p.inputStream.bufferedReader().use { it.readText() }
                p.waitFor()
                out.contains("uid=0")
            } catch (_: Exception) { false }
        }

    /** Checks whether we can get a root shell (triggers Magisk's auth prompt). */
    fun isAvailable(): Boolean = suBinary() != null

    /**
     * Executes a command as root via su, feeding the command via stdin (more
     * reliable than `su -c` on ROMs that mangle quoting). Returns trimmed
     * combined output, or empty string on failure.
     */
    fun exec(command: String): String {
        val su = suBinary() ?: return ""
        return try {
            val process = ProcessBuilder(su)
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { it.write("$command\n"); it.flush() }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)
            output.trim()
        } catch (e: Exception) {
            Log.w(TAG, "exec failed: ${e.message}")
            ""
        }
    }

    /** True if the MagiskSSH module (id=ssh) is installed. */
    fun isModuleInstalled(): Boolean =
        exec("ls /data/adb/modules/ssh/module.prop 2>/dev/null").isNotEmpty()

    /** Returns the installed module version, or empty. */
    fun moduleVersion(): String =
        exec("grep '^version=' /data/adb/modules/ssh/module.prop 2>/dev/null")
            .removePrefix("version=").trim()

    /** True if sshd is currently running. */
    fun isSshdRunning(): Boolean =
        exec("cat /data/ssh/sshd.pid 2>/dev/null | xargs -r kill -0 2>/dev/null && echo yes")
            .contains("yes")

    /** Installs the MagiskSSH module zip from the given path. */
    fun installModule(zipPath: String): String =
        exec("/data/adb/magisk/magisk --install-module '$zipPath' 2>&1")

    /** Removes the ssh module (module stays until reboot, or start script stops it). */
    fun uninstallModule(): String =
        exec("rm -rf /data/adb/modules/ssh 2>&1; echo done")
}
