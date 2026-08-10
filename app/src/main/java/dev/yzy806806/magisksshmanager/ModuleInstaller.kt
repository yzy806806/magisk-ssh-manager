package dev.yzy806806.magisksshmanager

import android.content.Context
import java.io.File

/**
 * Extracts the bundled MagiskSSH module zip from assets and installs it
 * via `magisk --install-module`.
 *
 * The zip is bundled into the APK at CI build time (see .github/workflows).
 */
object ModuleInstaller {

    private const val ASSET_NAME = "magisk_ssh.zip"
    private const val TMP_PATH = "/data/local/tmp/magisk_ssh.zip"

    /**
     * Installs the bundled module. Returns a user-facing result message.
     * After installation the module is registered but service.sh runs on next
     * reboot — the app can start sshd immediately via opensshd.init.
     */
    fun install(context: Context): String {
        // 1. extract zip from assets to app-private cache
        val cacheFile = File(context.cacheDir, ASSET_NAME)
        context.assets.open(ASSET_NAME).use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (!cacheFile.exists() || cacheFile.length() == 0L) {
            return "内置模块缺失（构建时未打包）"
        }

        // 2. copy to /data/local/tmp (readable by magisk); verify the copy landed.
        //    Fallback: install directly from the app cache dir (root can read it).
        val cpResult = RootShell.exec(
            "cp '$cacheFile.absolutePath' $TMP_PATH && chmod 644 $TMP_PATH && ls -la $TMP_PATH"
        )
        val zipToInstall: String
        if (cpResult.contains(TMP_PATH.substringAfterLast('/'))) {
            zipToInstall = TMP_PATH
        } else {
            // cp failed (SELinux/space) — install straight from app cache
            zipToInstall = cacheFile.absolutePath
        }

        // 3. install via magisk
        val result = RootShell.installModule(zipToInstall)
        return if (result.contains("Done") || result.contains("success") || result.contains("installed")) {
            "安装成功。重启手机后自动生效；或点“启动 sshd”立即启动。"
        } else {
            "安装结果: $result"
        }
    }
}
