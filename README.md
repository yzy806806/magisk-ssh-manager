# MagiskSSH Manager

给 [MagiskSSH](https://gitlab.com/d4rcm4rc/MagiskSSH)（OpenSSH for Magisk）模块做一个简单的管理界面，解决「改配置要手编辑文件、加公钥要 MT 管理器」的痛点。

## 功能

- **一键安装/卸载** MagiskSSH 模块（模块 zip 内置在 APK 里，无需单独下载）
- **sshd 开关**：启动/停止，实时状态
- **公钥管理**：添加/删除 authorized_keys
- **配置页**：SSH 端口、密码登录开关（改完自动重启 sshd）
- **卸载 App 不影响 SSH**：模块独立运行于 `/data/adb/modules/ssh/`

## 使用

1. 安装 APK（Release 下载）
2. 打开 App → 授予 root 权限
3. 一键安装模块 → 重启手机（或直接点「启动 sshd」）
4. 添加你的公钥 → SSH 登录

## 构建

GitHub Actions 自动构建：每次构建从上游 GitLab 拉取最新 MagiskSSH 模块 zip，打包进 APK。APK 产物见 Actions artifacts。

## 技术栈

- Kotlin + Jetpack Compose (Material 3)
- root 交互：Magisk `su`（RootShell 单例封装，便于扩展）

## 致谢

- [MagiskSSH](https://gitlab.com/d4rcm4rc/MagiskSSH) by D4rCM4rC — SSH 模块本体
