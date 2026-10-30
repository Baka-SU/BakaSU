package org.bakasu.bakasu.ui.wear

import org.bakasu.bakasu.R

/** Titles come from navigation, including when a restored list starts below its header. */
internal fun WearRoute.titleResource(mainTitle: Int): Int = when (this) {
    WearRoute.Main -> mainTitle

    is WearRoute.Operations -> R.string.wear_operations

    is WearRoute.Module -> R.string.module

    is WearRoute.ModuleAction -> R.string.action

    is WearRoute.ModuleUpdate -> R.string.module_update

    is WearRoute.ModuleInstall -> R.string.confirm_installation

    WearRoute.ModuleSort, WearRoute.ModuleRepoSort -> R.string.wear_sort

    WearRoute.ModuleRepo, is WearRoute.OnlineModule, is WearRoute.OnlineModuleSection -> R.string.module_repo

    WearRoute.ModuleRepoSearch, WearRoute.ModuleSearch, WearRoute.ModuleSearchResults -> R.string.search_modules

    is WearRoute.OnlineModuleDownload -> R.string.install

    is WearRoute.App -> R.string.profile

    WearRoute.AppFilter -> R.string.wear_filter_sort

    WearRoute.AppSearch, WearRoute.AppSearchResults -> R.string.search_apps

    WearRoute.Logs, is WearRoute.LogEntry -> R.string.sulog

    WearRoute.LogSearch -> R.string.sulog_search_placeholder

    WearRoute.LogOptions -> R.string.sulog_filter_title

    WearRoute.HomeVersionInfo -> R.string.home_version_info

    WearRoute.HomeStatusInfo -> R.string.home_status_info

    WearRoute.AllowlistBackup -> R.string.wear_allowlist_backup

    is WearRoute.Settings -> settingsCategoryTitle(category)

    is WearRoute.Selection -> when (selection) {
        WearSelection.PickerMode -> R.string.wear_picker_mode
        WearSelection.LinkMode -> R.string.wear_link_mode
        WearSelection.ScreenShape -> R.string.wear_screen_shape
        WearSelection.Language -> R.string.settings_language
        WearSelection.SuCompat -> R.string.settings_sucompat
        WearSelection.Palette -> R.string.dynamic_palette_style
        WearSelection.ColorSpec -> R.string.dynamic_color_spec
        WearSelection.TimeDisplay -> R.string.wear_time_display
    }

    WearRoute.Color -> R.string.theme_color

    WearRoute.Dpi -> R.string.wear_display_touch

    WearRoute.Templates, is WearRoute.TemplateEditor -> R.string.manage_app_profile

    WearRoute.OnlineTemplates -> R.string.wear_template_online

    WearRoute.SuSFS -> R.string.susfs_config_title

    WearRoute.DynamicManager -> R.string.dynamic_manager_title

    WearRoute.Umount -> R.string.umount_path_manager

    WearRoute.Uninstall, is WearRoute.UninstallFlash -> R.string.settings_uninstall

    WearRoute.KernelInstall, is WearRoute.BootFlash, is WearRoute.KernelFlash -> R.string.install

    WearRoute.LkmInstall -> R.string.Lkm_install_methods

    is WearRoute.Ak3Install -> R.string.GKI_install_methods

    WearRoute.Reboot -> R.string.reboot

    WearRoute.Bugreport -> R.string.send_log

    WearRoute.About -> R.string.about

    WearRoute.Licenses, is WearRoute.LicenseDetail -> R.string.open_source_license

    is WearRoute.Browser -> R.string.wear_link_mode
}
