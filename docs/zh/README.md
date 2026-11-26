# BakaSU
<img align='right' src='../BakaSU_Full.svg' width='220px' alt="BakaSU Icon">

曾用名：ReSukiSU

[English](../README.md) | **简体中文** 

一个 [`tiann/KernelSU`](https://github.com/tiann/KernelSU) 的下游分支, 添加了一些个人修改。

[![最新发行](https://img.shields.io/github/v/release/Baka-SU/BakaSU?label=Release&logo=github)](https://github.com/Baka-SU/BakaSU/releases/latest)
[![最新 CI 构建（nightly.link）](https://img.shields.io/badge/nightly.link-%E6%9C%80%E6%96%B0%20CI%20%E6%9E%84%E5%BB%BA-800080)](https://nightly.link/Baka-SU/BakaSU/workflows/build-manager/main)
[![频道](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/BakaSU_Grp)
[![Kernel License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![其他部分 License：GPL v3](https://img.shields.io/github/license/Baka-SU/BakaSU?logo=gnu)](../../LICENSE)

## 特性

1. 基于内核的 `su` 和权限管理。
2. 基于 [metamodules](https://kernelsu.org/zh_CN/guide/metamodule.html) 的模块系统：可插拔的模块架构。
3. [App Profile](https://kernelsu.org/zh_CN/guide/app-profile.html): 把 Root 权限关进笼子里。
4. 支持 non-GKI 与 GKI 1.0。
5. 可调整管理器外观，可自定义 susfs 配置。
6. 多管理器支持，默认支持使用 [官方KernelSU](https://github.com/tiann/KernelSU)/[KOWSU](https://github.com/KOWX712/KernelSU) 作为管理器与 BakaSU 内核共同工作

## 兼容状态

- BakaSU 官方支持 GKI 2.0 的设备（内核版本 5.10 以上）。

- 旧内核也是兼容的（3.4+），不过需要自己编译内核。

- 目前支持架构 : `arm64-v8a`、`armeabi-v7a`、`x86_64`。

- `Tracepoint Syscall Redirect Hook` 只支持在 GKI2 内核(5.10+) 工作

## Hook 模式
- `Tracepoint Syscall Redirect hook` 默认模式, 来自于 [上游](https://github.com/tiann/KernelSU), 但是只支持 GKI2 内核且为 `arm64-v8a` 或 `x86_64` 架构
- `Manual Hook` 兼容性最强的钩子，支持 Linux Kernel 3.4 - Linux Kernel 6.18
- `SUSFS Inline Hook` 一个来自 [SUSFS](https://github.com/simonpunk/susfs4ksu) 的 Hook, 类似于 `Manual Hook`, 但是由 `SUSFS` 项目，而非本项目

## 集成

请参考[文档](https://bakasu.org)

## 参与翻译

要将 BakaSU 翻译成您的语言，或完善现有的翻译，请使用 [Weblate](https://hosted.weblate.org/engage/bakasu/).

## 许可证

- 目录 `kernel` 下所有文件为 [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)。
- 美术及视觉资源（[`docs/BakaSU_Clean.svg`](../BakaSU_Clean.svg)、[`docs/BakaSU_Full.svg`](../BakaSU_Full.svg) 与 [`manager/app/src/main/res/drawable/ic_launcher_foreground.xml`](../../manager/app/src/main/res/drawable/ic_launcher_foreground.xml)）由 [OukaroMF](https://github.com/OukaroMF) 原创制作并采用分层版权协议，详见 [`ASSETS_LICENSE.md`](../../ASSETS_LICENSE.md) 与 [`ASSETS_LICENSE.zh-CN.md`](../../ASSETS_LICENSE.zh-CN.md)。
- 除上述文件及目录的其他部分均为 [GPL-3.0-or-later](https://www.gnu.org/licenses/gpl-3.0.html)。

## 致谢

- [weishu](https://github.com/sponsors/tiann) (KernelSU 作者)

## 本地化

在 Weblate 上对 BakaSU 的翻译做出贡献:

https://hosted.weblate.org/engage/bakasu/

[![翻译状态](https://hosted.weblate.org/widget/bakasu/multi-auto.svg)](https://hosted.weblate.org/engage/bakasu/)

## 鸣谢

- [KernelSU](https://github.com/tiann/KernelSU): 上游
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra)：分叉来源
- [kernel-assisted-superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/)：KernelSU 的灵感。
- [Magisk](https://github.com/topjohnwu/Magisk)：强大的 root 工具箱。
- [genuine](https://github.com/brevent/genuine/)：apk v2 签名验证。
- [Diamorphine](https://github.com/m0nad/Diamorphine)：一些 rootkit 技巧。
