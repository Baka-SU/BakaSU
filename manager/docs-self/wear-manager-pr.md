# Add a Wear OS Manager experience with Wear Material 3

This PR adapts ReSukiSU's root management workflows to watch screens and input. It adds a Wear Material 3 interface within the existing `:app` module, sharing ViewModels, use cases, repositories, and Android resources, plus a companion phone view for module WebUIs hosted on the watch.

## User-visible changes

### Navigation and presentation

- Navigate between **Home, Superuser, Modules, and Settings** by swiping horizontally, with page indicators, time text, and scroll indicators.
- Use Wear lists, cards, switches, dialogs, and bottom `EdgeButton` actions. Rotary input scrolls the current list.
- Show a black launch background, followed by a branded loading screen and visible loading, empty, and error states.
- Adapt to round and rectangular displays. Rectangular mode uses straight time, page, and scroll indicators and keeps list items at full size near the screen edges.
- Choose **fade and scale** or **horizontal slide** page transitions in **Settings → Display**. Transitions use the theme's motion scheme and respect reduced-motion settings.

### Root and capability awareness

- Reuse the phone Manager's capability checks to show the available pages and actions.
- Hide Superuser, Modules, and root-only settings when full root functionality is unavailable. Hide SUSFS settings when the kernel does not report SUSFS support.
- Update navigation when capabilities change, including leaving a detail page that is no longer available.
- Present Home status and version information using the phone Manager's existing data, including applicable SELinux, seccomp, hook, and module implementation details.

### Superuser, modules, and logs

- Search applications and modules through the existing search logic. Keep shared-UID application grouping and the phone Manager's authorization and profile behavior.
- Open application profiles, manage root access and module unmount behavior, and distinguish authorized applications with both text and visual emphasis.
- Install module ZIPs, enable or disable modules, uninstall them, and expose WebUI, action scripts, and updates when the module provides those capabilities.
- Provide Wear selection and progress pages for the existing boot-image, LKM, and AnyKernel3 installation workflows, subject to the existing installation environment checks.
- Browse SU logs, enable or disable logging, select log files, search, filter events, and clear logs through the existing log workflows.

### Pull gestures and secondary menus

- At the bottom of the Superuser or Modules list, a short upward pull followed by release refreshes the list with progress feedback.
- At the top of Home, Superuser, or Modules, pull down beyond the 48dp threshold and release to open reboot options, filtering/sorting, or module sorting respectively. The gesture must start at the list boundary; no timed hold is required.
- Pull down past the same threshold and release at the top of a secondary menu to close it. The bottom button has a downward arrow and closes it immediately.
- The additional Reboot, Filter and sort, and Sort entry rows are removed; touch gestures and equivalent accessibility actions provide menu access.
- Secondary menus use vertical slide transitions independently of the selected page animation, while respecting reduced-motion settings. Gesture feedback includes a top-edge indicator, haptics, and spring motion. Rotary input scrolls the list; continuing upward beyond its top can also open or close the menu using the existing rotary threshold.
- Preserve list state, search, and sorting when returning, and provide equivalent accessibility actions.

### Settings and SUSFS

- Organize settings into **General, Security, Advanced, Display**, and a conditional **SUSFS** category, with a separate About entry.
- Group related settings within each category and add icons to actionable rows while keeping section headings text-only.
- Put application language under **Display**, alongside color and background controls, application density scaling, screen shape, and page transitions.
- Density scaling applies within Manager and preserves system font scaling. Screen shape can follow the device or use the round/rectangular layout explicitly.
- Provide Wear SUSFS status and controls, standard settings, SUS Path, SUS Kstat, Open Redirect, and SUS Map configuration, plus configuration export, import, and reset. Operations reuse the phone SUSFS ViewModel and retain confirmation and result feedback.

### Files and module WebUI

- Provide **automatic, built-in, and system** file-picker modes. The built-in picker supports module/AnyKernel3 ZIPs, boot images, LKM files, background images, local error-report export, and SUSFS JSON/text selection, subject to the app's available file access.
- Open module WebUI on the watch when a system WebView is available, or request opening through a compatible phone Manager. Automatic mode selects the watch WebView when available and otherwise uses the phone path.
- Discover the companion using `resukisu_webui` and launch `RemoteWebUIActivity` through `RemoteActivityHelper`. Wear Data Layer channels serve module files and `ksu` bridge calls from the watch. The phone does not require root for this remote view; module operations execute on the watch.
- Discovery, unavailable-phone states, and dispatch failures have explicit feedback. Release resource shrinking retains the capability array through `raw/resukisu_wear_keep.xml`.

### Localization

- Reuse phone Manager resource IDs for shared text rather than maintaining duplicate Wear translations.
- Cover the remaining 68 Wear strings in all 44 existing language and region resource directories, including settings groups, display animations, menu actions, and companion-app guidance.
- Remove the Wear resources' `MissingTranslation` suppression.

## Project structure

The Gradle modules remain `:app`, `:baselineprofile`, and `:lint-rules`. Phone and Wear share the application package and resources. `MainActivity` selects the Wear interface using `PackageManager.FEATURE_WATCH`; there is no separate Wear application module.

Paths below are relative to `manager/app/src/main/java/com/resukisu/resukisu/`:

| Responsibility | Main implementation |
| --- | --- |
| Device routing and startup | `ui/MainActivity.kt`, `ui/StartupSplashGate.kt` |
| Wear navigation, screens, and theme | `ui/wear/`, with navigation in `WearManager.kt` and theming in `WearTheme.kt` |
| Reusable lists, gestures, indicators, and dialogs | `ui/component/wear/` |
| Settings navigation, choices, and list-group adapters | `ui/component/settings/`; the Wear switch wrapper is in `ui/component/wear/` |
| Shared business state and Wear orchestration | Existing ViewModels and `Wear*ViewModel` classes under `ui/viewmodel/` |
| File access and local error-report export | `data/file/WearFileRepository.kt` |
| Watch/phone opening and companion discovery | `data/network/WearLinkRepository.kt` |
| Remote module WebUI transport | `data/webui/WearWebUiBridge.kt`, `WearWebUiService.kt` |
| Companion phone WebUI | `ui/webui/RemoteWebUIActivity.kt` and the shared WebUI renderer/backend interfaces |
| Dependency injection | Existing repository and ViewModel registrations in `di/AppModules.kt` |

Wear Compose Foundation and Material 3 use the current catalog version, `1.7.0`. Lists use `TransformingLazyColumn` with `ScreenScaffold`; pagers go through `HorizontalPagerWithInteraction`. Settings adapters emit entries into the existing Wear list, and confirmations use `rememberWearConfirmDialog` over `rememberCustomDialog`. Root, authorization, module, and SUSFS operations reuse the existing ViewModels, domain use cases, and repositories. Wear preferences use the existing preference use cases; file access and companion transport remain in the data layer.

Resources live under `manager/app/src/main/res/`. Default Wear text is split between `values/wear_strings.xml` and `values/wear_features.xml`; each locale's `wear_strings.xml` contains the translated Wear text. Shared wording continues to use the existing phone resource IDs.

## Validation

- `assembleRelease`: passed.
- `lintRelease`: passed with **0 errors and 70 existing warnings**.
- Current Wear resource checks: XML parsed successfully; all 44 locale directories cover the 68 translatable strings, with no duplicate names in their Wear files.
- `git diff --check`: passed for the implementation changes.
- Manual Release-device check: the maintainer confirmed Superuser application-list loading works.

## Compatibility and remaining testing

Phone-side module WebUI requires a companion Manager that implements this feature, with the same application package name and signing certificate as the watch app, and working Wear Data Layer connectivity. Existing official phone builds without this support cannot receive these requests; the watch displays companion-app guidance. Dispatching an open request does not confirm that the phone loaded the page.

Current opening modes are automatic, watch WebView, and phone; an arbitrary external watch browser is not offered. Error-report export is local; a dedicated transfer-and-save workflow on the phone is not implemented.

The restored menu gestures still need verification on a rooted watch. Authorization changes, kernel/module installation and updates, SUSFS operations, and paired-phone WebUI need end-to-end device testing. Small round screens, rectangular screens, large system fonts, and translated layouts also need broader device coverage. These build and resource checks do not constitute full Wear OS quality acceptance.
