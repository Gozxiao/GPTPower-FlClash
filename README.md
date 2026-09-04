# GPTPower

面向 Xiaomi 13 `fuxi`、HyperOS 4、Android 17 / API 37 的 KernelSU + modern LSPosed 组合模块。

## 安装

1. 在 KernelSU 中刷入 `GPTPower-KSU.zip` 并重启。
2. KSU 模块会在需要时安装或升级内置的 `GPTPower-LSPosed.apk`。
3. 第一次安装后打开 LSPosed：模块 → GPTPower → 启用。
4. 推荐作用域：System Framework、FlClash、ChatGPT，然后重启。
5. 确认 ChatGPT 仍是默认 Assistant 和 VoiceInteractionService。

KSU 模块会在开机完成时把 `power_button_long_press` 修复为 `5`，并在 3 秒、10 秒时复查。低频守护默认每 10 秒检查一次；如需关闭，编辑模块目录内的 `config.conf`：

```text
POWER_GUARD=0
```

修改配置后重启模块或设备生效。

## 工作方式

- 已有任意 VPN：完全放行 HyperOS 原始长按电源 Assistant 调用，不取得 VPN 所有权。
- 无 VPN：LSPosed 在 `PhoneWindowManager.launchAssistAction(...)` 识别电源键来源，通知 KSU helper 冷启动 FlClash；VPN Ready 后按原对象和原参数重放同一方法。
- 直接点击或从最近任务返回 ChatGPT：异步请求 FlClash VPN，不阻塞 Activity 启动。
- Voice Session 已隐藏且 ChatGPT 不在前台：仅当 VPN 是 GPTPower 启动时，通知 KSU helper `force-stop` FlClash。
- 连续三次 VPN 启动失败：本次开机停止拦截长按电源，系统调用保持 fail-open。

LSPosed 不调用 ChatGPT Activity、`input keyevent` 或 `cmd voiceinteraction show`。KSU 日志位于 `/data/adb/gptpower/log/`。

## KernelSU 操作菜单

在 KernelSU 中点击 GPTPower 的“操作”，可修复电源键设置、启动/关闭/检测 VPN、重装内置 APK并输出诊断日志。

## 构建

GitHub Actions 在 `push` 或手动触发时使用 JDK 17、API 37 和 modern libxposed API 102，同时产出：

- `GPTPower-LSPosed.apk`
- `GPTPower-KSU.zip`

HyperOS 私有方法只能通过 fuxi 实机日志完成最终验收；Hook 未匹配或反射失败时不会阻止系统原行为。
