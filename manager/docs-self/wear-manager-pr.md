# Wear OS Manager architecture and implementation

Wear Manager lives in the existing `:app` module alongside the phone Manager. The Gradle modules remain `:app`, `:baselineprofile`, and `:lint-rules`. Both interfaces share the application package, Android resources, and business layers.

## Project structure

Paths are relative to `manager/app/src/main/java/com/resukisu/resukisu/`.

| Layer | Location | Responsibility |
| --- | --- | --- |
| Application entry | `ui/MainActivity.kt`, `ui/StartupSplashGate.kt` | Device routing and startup state |
| Wear screens | `ui/wear/` | Navigation, screen composition, and Wear theme |
| Wear components | `ui/component/wear/` | Lists, cards, gestures, transitions, indicators, dialogs, and profile configuration |
| Settings components | `ui/component/settings/` | Navigation rows, choices, and segmented list adapters |
| Presentation state | `ui/viewmodel/` | Shared business ViewModels and Wear-specific orchestration |
| Domain operations | `domain/usecase/` | Profile, module, settings, installation, and log operations |
| Data access | `data/` | Persistence, platform access, networking, file access, and WebUI transport |
| Dependency injection | `di/AppModules.kt` | Repository, use-case, and ViewModel registrations |

Android resources live in `manager/app/src/main/res/`. Shared text uses existing phone resource IDs; Wear-specific text uses `wear_strings.xml` and `wear_features.xml` with locale resources.

## Entry and navigation

`MainActivity` detects `PackageManager.FEATURE_WATCH` and renders `WearManagerTheme` with the Wear startup or Manager screen. Startup state controls the loading, failure, and ready views.

`WearManager.kt` owns Home, Superuser, Modules, and Settings navigation. `HorizontalPagerScaffold` and `HorizontalPagerWithInteraction` handle the main pages. Secondary routes use a parent stack and saveable page state. Kernel capabilities determine which pages and actions are available.

## UI composition and motion

The interface uses Wear Compose Foundation and Material 3. `AppScaffold` owns time text; `WearList` combines `ScreenScaffold`, `TransformingLazyColumn`, scroll indicators, rotary scrolling, and bottom `EdgeButton` actions. Shared components apply the list transformation spec, and section subtitles are centered.

`WearRefreshGesture` handles boundary pulls: a downward pull at the main list's top opens its menu, an upward pull at a menu's bottom closes it, and an upward pull at the Superuser or Modules list's bottom refreshes that list. Crossing the threshold and releasing commits the action. Only the active screen handles boundary gestures, and menus open at their heading.

`WearPageTransition` moves the menu and its parent through the same full-size container. Opening moves both screens downward; returning moves both upward. The departing screen remains until both transitions finish. Menu motion uses the theme's spatial animation independently of the page animation preference and respects system reduced motion.

## State and business operations

Screens collect ViewModel state and dispatch actions through the existing dependency chain:

```text
Wear screen -> ViewModel -> Use case -> Repository -> ksud / Android platform
```

Home, Superuser, Modules, Settings, SU logs, installation, and SUSFS reuse their existing ViewModels and operations. Wear-specific ViewModels coordinate file selection, link opening, preferences, and installation presentation. Koin supplies dependencies through the existing modules and parameterized screen ViewModels.

Settings categories compose the shared Wear settings wrappers and segmented list adapters. `WearTheme.kt` applies the color palette, background, application density, screen shape, and motion preferences.

## App Profile and templates

Application details use `AppProfileViewModel` for authorization and profile updates. `WearAppProfileConfig` presents default, template, and custom root modes, including UID, GID, groups, capabilities, namespace, flags, and SELinux configuration. Non-root profiles use the default or custom module-unmount setting. Template selection copies profile fields through the same shared model and save operation used by the phone Manager.

`WearTemplatePage` loads local templates without automatic online synchronization. Online browsing uses `ProfileTemplateRepository` with the existing index and template URLs and JSON parser. Results remain in ViewModel memory while the watch displays the list and details. Explicitly adding a selected template saves it to the watch's local store; local templates use the shared editor, import, and export operations.

## Files and WebUI

`data/file/WearFileRepository.kt` supplies file browsing, selection, and local report export. Wear file pages pass selected files to the existing installation or settings operations.

`data/network/WearLinkRepository.kt` resolves watch WebView and phone opening. For phone module WebUI, it discovers the companion capability and launches `ui/webui/RemoteWebUIActivity.kt` through `RemoteActivityHelper`. `data/webui/WearWebUiBridge.kt` and `WearWebUiService.kt` transport module files and bridge calls over Wear Data Layer channels. The phone renders the page while the watch executes operations against its local module.
