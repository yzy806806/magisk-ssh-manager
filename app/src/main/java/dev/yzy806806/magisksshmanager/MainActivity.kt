package dev.yzy806806.magisksshmanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ManagerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerApp() {
    val context = LocalContext.current
    var rootOk by remember { mutableStateOf<Boolean?>(null) }
    var tab by remember { mutableStateOf(0) }
    var sshdRunning by remember { mutableStateOf(false) }
    var moduleInstalled by remember { mutableStateOf(false) }
    var moduleVersion by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            moduleInstalled = RootShell.isModuleInstalled()
            moduleVersion = RootShell.moduleVersion()
            sshdRunning = moduleInstalled && RootShell.isSshdRunning()
        }
    }

    LaunchedEffect(Unit) {
        rootOk = RootShell.isAvailable()
        refresh()
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("MagiskSSH Manager") })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🏠") },
                    label = { Text("状态") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("🔑") },
                    label = { Text("公钥") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Text("⚙️") },
                    label = { Text("配置") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                rootOk == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                rootOk == false -> RootNotAvailable()
                else -> when (tab) {
                    0 -> StatusTab(
                        moduleInstalled, moduleVersion, sshdRunning, loading,
                        onInstall = {
                            loading = true
                            val result = ModuleInstaller.install(context)
                            loading = false
                            toast(result)
                            refresh()
                        },
                        onUninstall = {
                            loading = true
                            RootShell.uninstallModule()
                            loading = false
                            refresh()
                        },
                        onToggleSshd = {
                            loading = true
                            val result = if (sshdRunning) SshConfig.stopDaemon()
                            else SshConfig.startDaemon()
                            loading = false
                            toast(result)
                            refresh()
                        }
                    )
                    1 -> KeysTab()
                    2 -> ConfigTab(onChanged = { loading = true; SshConfig.restartDaemon(); loading = false; toast("已重启 sshd") })
                }
            }
        }
    }
}

@Composable
fun RootNotAvailable() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("未获取到 root 权限", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("请确保设备已安装 Magisk，并允许本应用获取 root 权限。", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun StatusTab(
    installed: Boolean,
    version: String,
    running: Boolean,
    loading: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onToggleSshd: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模块状态", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (installed) "已安装 (v$version)" else "未安装",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    if (running) "● sshd 运行中" else "○ sshd 未运行",
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    "配置目录: /data/ssh\n密钥文件: /data/ssh/root/.ssh/authorized_keys",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        if (!installed) {
            Button(onClick = onInstall, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "安装中…" else "一键安装 MagiskSSH 模块")
            }
            Text(
                "安装后需重启手机生效（或点击下方“启动 sshd”立即启动）。模块独立于本应用，卸载本应用不影响 SSH 功能。",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Button(onClick = onToggleSshd, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "处理中…" else if (running) "停止 sshd" else "启动 sshd")
            }
            OutlinedButton(onClick = onUninstall, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text("卸载模块")
            }
        }
    }
}

@Composable
fun KeysTab() {
    var keys by remember { mutableStateOf(listOf<String>()) }
    var newKey by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        keys = withContext(Dispatchers.IO) { SshConfig.listKeys() }
    }

    fun refreshKeys() {
        keys = RootShell.listKeys()
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("已授权公钥 (${keys.size})", style = MaterialTheme.typography.titleMedium)

        if (keys.isEmpty()) {
            Text("暂无公钥，添加后即可用对应私钥 SSH 登录。", style = MaterialTheme.typography.bodyMedium)
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(keys) { key ->
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            key.take(70) + if (key.length > 70) "…" else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        TextButton(onClick = {
                            SshConfig.removeKey(key)
                            refreshKeys()
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        if (!expanded) {
            Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("添加公钥")
            }
        } else {
            OutlinedTextField(
                value = newKey,
                onValueChange = { newKey = it },
                label = { Text("粘贴公钥 (ssh-ed25519 AAAA… 或 ssh-rsa AAAA…)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (SshConfig.addKey(newKey)) {
                        toast("已添加")
                        newKey = ""
                        expanded = false
                        refreshKeys()
                    } else {
                        toast("公钥为空或已存在")
                    }
                }) { Text("保存") }
                OutlinedButton(onClick = { expanded = false; newKey = "" }) { Text("取消") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigTab(onChanged: () -> Unit) {
    var port by remember { mutableStateOf("") }
    var passwordAuth by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (!loaded) {
            port = withContext(Dispatchers.IO) { SshConfig.getPort().toString() }
            passwordAuth = withContext(Dispatchers.IO) { SshConfig.isPasswordAuthEnabled() }
            loaded = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("监听配置", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit).take(5) },
                    label = { Text("SSH 端口") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("允许密码登录", modifier = Modifier.weight(1f))
                    Switch(
                        checked = passwordAuth,
                        onCheckedChange = { passwordAuth = it }
                    )
                }
                Text(
                    "⚠️ 密码登录不安全，建议仅使用公钥。若启用请设置强密码。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Button(
            onClick = {
                val p = port.toIntOrNull()
                if (p == null || p !in 1..65535) {
                    Toast.makeText(context, "端口无效", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                SshConfig.setPort(p)
                SshConfig.setPasswordAuth(passwordAuth)
                saved = true
                onChanged()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存并重启 sshd") }

        if (saved) {
            Text("已保存", color = MaterialTheme.colorScheme.primary)
        }
    }
}
