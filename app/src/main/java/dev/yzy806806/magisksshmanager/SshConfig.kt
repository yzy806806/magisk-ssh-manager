package dev.yzy806806.magisksshmanager

/**
 * Reads/writes the MagiskSSH runtime configuration:
 *   /data/ssh/sshd_config          — daemon config
 *   /data/ssh/root/.ssh/authorized_keys — authorized public keys
 */
object SshConfig {

    const val CONFIG_PATH = "/data/ssh/sshd_config"
    const val KEYS_PATH = "/data/ssh/root/.ssh/authorized_keys"

    /** Full raw sshd_config content. */
    fun readConfig(): String = RootShell.exec("cat $CONFIG_PATH 2>/dev/null")

    /** Writes the full sshd_config (via a temp file to avoid partial writes). */
    fun writeConfig(content: String): Boolean {
        val tmp = "$CONFIG_PATH.tmp"
        val ok = RootShell.exec(
            "echo '${content.replace("'", "'\\''")}' > $tmp && " +
                "chmod 600 $tmp && mv $tmp $CONFIG_PATH && echo OK"
        )
        return ok.contains("OK")
    }

    /** Port the daemon listens on (from config, default 22). */
    fun getPort(): Int {
        val line = RootShell.exec("grep -E '^Port ' $CONFIG_PATH 2>/dev/null")
            .trim().removePrefix("Port").trim()
        return line.toIntOrNull() ?: 22
    }

    /** Set the listen port. */
    fun setPort(port: Int): Boolean {
        val cfg = readConfig()
        val updated = if (Regex("(?m)^Port .*").containsMatchIn(cfg)) {
            cfg.replace(Regex("(?m)^Port .*"), "Port $port")
        } else {
            cfg + "\nPort $port\n"
        }
        return writeConfig(updated)
    }

    /** Password auth enabled? */
    fun isPasswordAuthEnabled(): Boolean {
        val line = RootShell.exec("grep -E '^PasswordAuthentication ' $CONFIG_PATH 2>/dev/null")
        return line.contains("yes")
    }

    fun setPasswordAuth(enabled: Boolean): Boolean {
        val cfg = readConfig()
        val value = if (enabled) "yes" else "no"
        val updated = if (Regex("(?m)^PasswordAuthentication .*").containsMatchIn(cfg)) {
            cfg.replace(Regex("(?m)^PasswordAuthentication .*"), "PasswordAuthentication $value")
        } else {
            cfg + "\nPasswordAuthentication $value\n"
        }
        return writeConfig(updated)
    }

    // ---- authorized_keys ----

    /** List of authorized public keys (one per line). */
    fun listKeys(): List<String> =
        RootShell.exec("cat $KEYS_PATH 2>/dev/null")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    /** Adds a public key (dedupe by content). Returns true if added. */
    fun addKey(pubKey: String): Boolean {
        val key = pubKey.trim()
        if (key.isEmpty()) return false
        val existing = listKeys()
        if (existing.contains(key)) return false
        val all = (existing + key).joinToString("\n") + "\n"
        return writeKeys(all)
    }

    /** Removes a public key by content. Returns true if removed. */
    fun removeKey(pubKey: String): Boolean {
        val existing = listKeys()
        val updated = existing.filter { it != pubKey.trim() }
        if (updated.size == existing.size) return false
        return writeKeys(updated.joinToString("\n") + "\n")
    }

    private fun writeKeys(content: String): Boolean {
        val tmp = "$KEYS_PATH.tmp"
        val ok = RootShell.exec(
            "mkdir -p ${KEYS_PATH.substringBeforeLast('/')} && " +
                "echo '${content.replace("'", "'\\''")}' > $tmp && " +
                "chmod 600 $tmp && mv $tmp $KEYS_PATH && echo OK"
        )
        return ok.contains("OK")
    }

    // ---- daemon control ----

    /** Starts the ssh daemon (module init script). */
    fun startDaemon(): String =
        RootShell.exec("sh /data/adb/modules/ssh/opensshd.init start 2>&1")

    /** Stops the ssh daemon. */
    fun stopDaemon(): String =
        RootShell.exec("sh /data/adb/modules/ssh/opensshd.init stop 2>&1")

    /** Restarts the daemon after config changes. */
    fun restartDaemon(): String {
        stopDaemon()
        return startDaemon()
    }
}
