# BakaSU
<img align='right' src='BakaSU_Full.svg' width='220px' alt="BakaSU Icon">

Formerly known as: ReSukiSU

**English** | [简体中文](./zh/README.md)

A [`tiann/KernelSU`](https://github.com/tiann/KernelSU)'s downstream, added some personal changes.

[![Latest release](https://img.shields.io/github/v/release/Baka-SU/BakaSU?label=Release&logo=github)](https://github.com/Baka-SU/BakaSU/releases/latest)
[![Latest CI build (nightly.link)](https://img.shields.io/badge/nightly.link-Latest%20CI%20Build-800080)](https://nightly.link/Baka-SU/BakaSU/workflows/build-manager/main)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/BakaSU_Grp)
[![Kernel License: GPL v2](https://img.shields.io/badge/License-GPL%20v2-orange.svg?logo=gnu)](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html)
[![Other part License：GPL v3](https://img.shields.io/github/license/Baka-SU/BakaSU?logo=gnu)](../LICENSE)

## Features

1. Kernel-based `su` and root access management
2. Module system based on [metamodules](https://kernelsu.org/guide/metamodule.html): Pluggable infrastructure for systemless modifications.
3. [App Profile](https://kernelsu.org/guide/app-profile.html): Lock up the root power in a cage
4. Support non-GKI and GKI 1.0
5. Tweaks to the manager theme and the built-in susfs management tool.
6. Multi manager support, for default [Official KernelSU](https://github.com/tiann/KernelSU)/[KOWSU](https://github.com/KOWX712/KernelSU) is supported work as manager with BakaSU's kernel

## Compatibility Status

- BakaSU officially supports Android GKI 2.0 devices (kernel 5.10+).

- Older kernels (3.4+) are also compatible, but the kernel will have to be built manually.

- Currently, only `arm64-v8a`, `armeabi-v7a` and `X86_64`are supported.

- [SUSFS](https://gitlab.com/simonpunk/susfs4ksu) in this project is **ONLY** support backport to kernel 4.3+

- `Tracepoint Syscall Redirect hook` is only support with GKI2(5.10+) kernel

## Hook Mode
- `Tracepoint Syscall Redirect hook` The default hook mode, from [upstream](https://github.com/tiann/KernelSU), but its only support GKI2 kernel with `arm64-v8a` or `x86_64` ABI
- `Manual Hook` The most compatible Hook, support from Linux kernel 3.4 to Linux kernel 6.18
- `SUSFS Inline Hook` An hook from [SuSFS](https://github.com/simonpunk/susfs4ksu), like `Manual Hook`, but provide from `SUSFS` project, not this project

## Integration

See the [documentation](https://bakasu.org).

## Translation

If you want to submit a translation for the manager, please go to [Weblate](https://hosted.weblate.org/engage/bakasu/).

## Sponsor

- [weishu](https://github.com/sponsors/tiann) (author of KernelSU)

## License

- The file in the “kernel” directory is under [GPL-2.0-only](https://www.gnu.org/licenses/old-licenses/gpl-2.0.en.html) license.
- The artwork and visual files ([`docs/BakaSU_Clean.svg`](./BakaSU_Clean.svg), [`docs/BakaSU_Full.svg`](./BakaSU_Full.svg), and [`manager/app/src/main/res/drawable/ic_launcher_foreground.xml`](../manager/app/src/main/res/drawable/ic_launcher_foreground.xml)) are created by [OukaroMF](https://github.com/OukaroMF) under a layered license arrangement. See [`ASSETS_LICENSE.md`](../ASSETS_LICENSE.md) and [`ASSETS_LICENSE.zh-CN.md`](../ASSETS_LICENSE.zh-CN.md) for details.
- Except for the files or directories mentioned above, all other parts are under [GPL-3.0 or later](https://www.gnu.org/licenses/gpl-3.0.html) license.

## Localization

Help translate BakaSU on Weblate:

https://hosted.weblate.org/engage/bakasu/

[![Localization Status](https://hosted.weblate.org/widget/bakasu/multi-auto.svg)](https://hosted.weblate.org/engage/bakasu/)

## Credit

- [KernelSU](https://github.com/tiann/KernelSU): upstream
- [SukiSU-Ultra/SukiSU-Ultra](https://github.com/SukiSU-Ultra/SukiSU-Ultra): fork source
- [Kernel-Assisted Superuser](https://git.zx2c4.com/kernel-assisted-superuser/about/): The KernelSU idea.
- [Magisk](https://github.com/topjohnwu/Magisk): The powerful root tool.
- [genuine](https://github.com/brevent/genuine/): APK v2 signature validation.
- [Diamorphine](https://github.com/m0nad/Diamorphine): Some rootkit skills.
