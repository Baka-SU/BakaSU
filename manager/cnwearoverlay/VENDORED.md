# Vendored cnwearoverlay

Source: <https://github.com/Star-ZER0/cnwearoverlay>, tag `1.0.0` (LGPL-3.0, see `LICENSE`).

Kept in-tree so the Manager builds without a git submodule and without network access to a
third-party repository:

* `plugin/` — the Gradle plugin `cc.star0.wear.lib.cnwearoverlay`. It instruments
  `androidx.wear.compose` rotary haptics and `View.performHapticFeedback` call sites, and injects
  the runtime jar plus the `com.google.wear.input.WearHapticFeedbackConstants` stub.
* `runtime/` — the runtime helper compiled into the plugin's embedded jars.

It replaces the earlier `miwearhaptics` submodule, which upstream deprecated in favour of this
project. Consumed through `pluginManagement { includeBuild("cnwearoverlay") }`; upgrade by copying
a new upstream tag and updating this file.

Do not edit these sources in place: changes belong to the upstream project first.