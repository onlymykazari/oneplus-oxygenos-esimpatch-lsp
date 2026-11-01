# oneplus-oxygenos-esimpatch-lsp

[English](README.md) | **简体中文**

一个 LSPosed（Vector）模块，用于在 **一加 13T 刷成 OxygenOS 13s 固件**后，把系统里"半残"的 eSIM 入口接回真正可用的地方。

> **建议与 [`kinginu/oneplus13t-esim`](https://github.com/kinginu/oneplus13t-esim) 配合使用**：那个 Magisk 模块负责让设置里出现 eSIM 入口，本模块在此基础上把入口的动作重定向到能用的地方。

## 背景

一加 13T（国行）刷 OxygenOS 13s 后：

- 内部 eUICC（NXP SN220U）是**死的**——硬件在，但 CN 板没个性化，modem 读到 `CARDSTATE_ABSENT`，AP 层模块修不了；
- Google LPA 的软件栈是**完整的**，但"添加 eSIM profile"这一步会卡在 `0x20009`（框架不把可移除卡的 EID 发布到 public euicc API）；
- 设置里的 eSIM 页面基本是空的，单独的入口还被 OnePlus 隐藏了。

所以真正能用的路径是：**插一张可移除 eUICC 卡（5ber / eSTK.me / 9eSIM 等），用 EasyEUICC / NekokoLPA 这类第三方 LPA 下载**。本模块就是把系统设置里那些走不通的入口，重定向到这些能用工具上。

## 功能

### Hook 1：设置入口 → Google LPA

`com.android.phone` 的 `EsimSettingsActivity`（即设置里"SIM & eSIM settings"那一项）启动后，直接跳转到 Google LPA 的原生管理页（`MANAGE_EMBEDDED_SUBSCRIPTIONS` → `CurrentProfileListActivity`），显示卡里的 profile 列表。

### Hook 2：Add SIM → EasyEUICC

`com.google.android.euicc` 的 `ProfileListFragment.mButtonAddProfile`（即 Google LPA 里的"Add SIM"）点击后，跳转到 **EasyEUICC 的 `DownloadWizardActivity`**，直接进添加 eSIM 的下载向导。

> 为什么不用 NekokoLPA：NekokoLPA 是 Flutter 单 Activity 应用，UI 和路由都在 Dart 层，外部 Intent 无法定位到它的"添加"子页，只能落在主界面。EasyEUICC 有原生的 `DownloadWizardActivity`，能直接进下载流程。

## 适用范围

**设备**：一加 13T（`PKX110` / `CPH2723`），刷 OxygenOS 13s 固件，已 root（Magisk / KernelSU）+ 装好 LSPosed/Vector。

**前提**：

1. 已安装 [`kinginu/oneplus13t-esim`](https://github.com/kinginu/oneplus13t-esim)（让设置里出现 eSIM 入口）；
2. 已安装 **EasyEUICC**（`im.angry.easyeuicc.ce`），Hook 2 依赖它；
3. 插了可移除 eUICC 卡（5ber / eSTK.me / 9eSIM），并把卡的 **ATR Mode 切到 eUICC**；
4. 不要碰 OPPO 的 eSIM 主开关（那是内部死 eUICC 的，开了只会断 SIM2）。

**不适用**：真·13S（有可用内部 eUICC）、非 OxygenOS 系统（ColorOS 需要别的方案）、没有 root 的设备。

## 安装

1. 安装 `dist/EsimPatch-v1.2.apk`；
2. 打开 LSPosed/Vector，启用 EsimPatch；
3. **作用域勾选两个**：`com.android.phone` 和 `com.google.android.euicc`；
4. 重启（或至少杀掉这两个进程）。

## 验证

- 设置 → 移动网络 → SIM & eSIM settings：应该直接进 Google LPA 的 Carrier 页面（列出 profile + Add SIM + Reset carriers）；
- 点 **Add SIM**：应该直接进 EasyEUICC 的"Select or confirm the eSIM..."下载向导。

## 已知限制

- **下载仍受 `0x20009` 限制**：本模块只是把入口转到 EasyEUICC，Google LPA 自己的下载流程（不经本模块时）依然会卡住。
- **类名/字段名依赖当前固件**：`EsimSettingsActivity`、`ProfileListFragment`、`mButtonAddProfile` 都是当前 OxygenOS 13s 版本的符号。OTA 升级后可能失效，模块日志（tag `EsimPatch`）会打印 `class not found` / `hooked Google LPA failed`，重新适配即可。
- **弹窗**：NekokoLPA 每次启动的 "Keep this eUICC available" 提示是它自己在 Dart 层弹的，本模块不涉及（本模块改用 EasyEUICC，不会触发它）。

## 构建

需要 Android SDK build-tools + 一个 `android.jar`：

```bash
./build.sh
```

产物在 `dist/EsimPatch-v1.2.apk`。

## License

MIT
