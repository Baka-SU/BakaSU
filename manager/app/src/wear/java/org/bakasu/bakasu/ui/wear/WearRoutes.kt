package org.bakasu.bakasu.ui.wear

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The settings pages that open from the Wear settings root. */
@Serializable
enum class WearSettingsCategory { General, Security, Advanced, Display }

@Serializable
enum class WearOperationsPage { Home, SuperUser, Modules, Logs, Repository, DynamicManagerApps, Umount, Templates, OnlineTemplates, TemplateChoice }

/** The settings that are chosen from a single list of options. */
@Serializable
enum class WearSelection { PickerMode, LinkMode, ScreenShape, Language, SuCompat, Palette, ColorSpec, TimeDisplay, ToastDisplay }

/** Navigation destinations of [WearManagerScreen]; [Main] is the pager with the four top-level pages. */
@Serializable
sealed interface WearRoute : NavKey {
    @Serializable
    data object Main : WearRoute

    @Serializable
    data class Operations(val page: WearOperationsPage) : WearRoute

    @Serializable
    data class Module(val id: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class ModuleAction(val id: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class ModuleUpdate(val id: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class ModuleChangelog(val id: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class ModuleInstall(val uri: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data object ModuleSort : WearRoute, NeedsRoot

    @Serializable
    data object ModuleRepo : WearRoute, NeedsRoot

    @Serializable
    data object ModuleRepoSearch : WearRoute, NeedsRoot

    @Serializable
    data object ModuleRepoSearchResults : WearRoute, NeedsRoot

    @Serializable
    data object ModuleRepoSort : WearRoute, NeedsRoot

    @Serializable
    data object Downloads : WearRoute, NeedsRoot

    @Serializable
    data class Download(val moduleId: String, val downloadId: Int? = null) :
        WearRoute,
        NeedsRoot

    @Serializable
    data object RepositorySources : WearRoute, NeedsRoot

    @Serializable
    data object AddRepository : WearRoute, NeedsRoot

    @Serializable
    data class RepositorySource(val url: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class OnlineModule(val id: String, val repositoryUrl: String) :
        WearRoute,
        NeedsRoot

    /** The README, releases or information of a repository module. */
    @Serializable
    data class OnlineModuleSection(val id: String, val repositoryUrl: String, val section: String) :
        WearRoute,
        NeedsRoot

    /**
     * The download of one module file, with what the file must match. Repository downloads and
     * module updates share it; an [update] installs right away, as the update page already asked.
     */
    @Serializable
    data class OnlineModuleDownload(
        val name: String,
        val fileName: String,
        val url: String,
        val size: Long,
        val sha256: String,
        val version: String = "",
        val update: Boolean = false,
        val verificationRequired: Boolean = false,
        val iconUrl: String? = null,
        val iconOrigin: String? = null,
        val moduleId: String? = null,
    ) : WearRoute,
        NeedsRoot

    @Serializable
    data object ModuleSearch : WearRoute, NeedsRoot

    @Serializable
    data object ModuleSearchResults : WearRoute, NeedsRoot

    @Serializable
    data class App(val uid: Int, val packageName: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AffectedApps(val uid: Int, val packageName: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AppActions(val uid: Int, val packageName: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AppProfileMode(val uid: Int, val packageName: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AppProfileTemplateChoice(val uid: Int, val packageName: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AppProfileTemplateSearch(val uid: Int, val packageName: String, val query: String = "") :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AppProfileTemplateResults(val uid: Int, val packageName: String, val query: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class AppProfileCustom(val uid: Int, val packageName: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data object AppFilter : WearRoute, NeedsRoot

    @Serializable
    data object AppSearch : WearRoute, NeedsRoot

    @Serializable
    data object AppSearchResults : WearRoute, NeedsRoot

    @Serializable
    data object Logs : WearRoute, NeedsRoot

    @Serializable
    data object HomeVersionInfo : WearRoute

    @Serializable
    data object HomeStatusInfo : WearRoute

    @Serializable
    data object AllowlistBackup : WearRoute, NeedsRoot

    @Serializable
    data object LogOptions : WearRoute, NeedsRoot

    @Serializable
    data class LogEntry(val key: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data object LogSearch : WearRoute, NeedsRoot

    @Serializable
    data object LogSearchResults : WearRoute, NeedsRoot

    @Serializable
    data class Settings(val category: WearSettingsCategory) : WearRoute

    @Serializable
    data class Selection(val selection: WearSelection) : WearRoute

    @Serializable
    data object Color : WearRoute

    @Serializable
    data object CustomColor : WearRoute

    @Serializable
    data class ColorHex(val initial: String) : WearRoute

    @Serializable
    data object Dpi : WearRoute

    @Serializable
    data object Templates : WearRoute, NeedsRoot

    @Serializable
    data object TemplateSearch : WearRoute, NeedsRoot

    @Serializable
    data object TemplateSearchResults : WearRoute, NeedsRoot

    @Serializable
    data object OnlineTemplates : WearRoute, NeedsRoot

    @Serializable
    data object OnlineTemplateSearch : WearRoute, NeedsRoot

    @Serializable
    data object OnlineTemplateSearchResults : WearRoute, NeedsRoot

    @Serializable
    data class OnlineTemplateDetail(val id: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data class TemplateEditor(val id: String, val readOnly: Boolean, val creation: Boolean) :
        WearRoute,
        NeedsRoot

    @Serializable
    data object SuSFS : WearRoute, NeedsRoot

    @Serializable
    data object DynamicManager : WearRoute, DynamicManagerRoute

    @Serializable
    data object DynamicManagerManual : WearRoute, DynamicManagerRoute

    @Serializable
    data object DynamicManagerApps : WearRoute, DynamicManagerRoute

    @Serializable
    data object DynamicManagerSize : WearRoute, DynamicManagerRoute

    @Serializable
    data object DynamicManagerHash : WearRoute, DynamicManagerRoute

    @Serializable
    data object DynamicManagerSearch : WearRoute, DynamicManagerRoute

    @Serializable
    data object Umount : WearRoute, NeedsRoot

    @Serializable
    data object AddUmount : WearRoute, NeedsRoot

    @Serializable
    data object UmountSearch : WearRoute, NeedsRoot

    @Serializable
    data class UmountSearchResults(val query: String) :
        WearRoute,
        NeedsRoot

    @Serializable
    data object Uninstall : WearRoute, NeedsRoot

    /** Restoring the stock image or uninstalling permanently. */
    @Serializable
    data class UninstallFlash(val restore: Boolean) : WearRoute

    @Serializable
    data object KernelInstall : WearRoute

    @Serializable
    data object LkmInstall : WearRoute

    @Serializable
    data class Ak3Install(val uri: String? = null) : WearRoute

    /** Patching the boot image with the choices of [LkmInstall], as the phone's Flash screen does. */
    @Serializable
    data class BootFlash(
        val bootUri: String?,
        val lkmUri: String?,
        val kmi: String?,
        val ota: Boolean,
        val partition: String?,
        val allowShell: Boolean,
        val enableAdb: Boolean,
        val forceBackup: Boolean,
    ) : WearRoute

    @Serializable
    data class KernelFlash(val uri: String, val slot: String?, val skipKsud: Boolean) : WearRoute

    @Serializable
    data object Reboot : WearRoute, NeedsRoot

    @Serializable
    data object Bugreport : WearRoute

    @Serializable
    data object About : WearRoute

    @Serializable
    data object Licenses : WearRoute

    @Serializable
    data class LicenseDetail(val index: Int) : WearRoute

    @Serializable
    data class Browser(val url: String) : WearRoute

    /** Destinations in a feature's Navigation 3 stack; its owner retains drafts and operations. */
    @Serializable
    sealed interface SubPage : WearRoute

    @Serializable
    data object ProfileFields : SubPage

    @Serializable
    data class ProfileField(val field: Int) : SubPage

    @Serializable
    data class TemplateField(val field: Int) : SubPage

    @Serializable
    data object RepositoryUrl : SubPage

    @Serializable
    data object RepositoryApi : SubPage

    @Serializable
    data object RepositoryName : SubPage

    @Serializable
    data object UmountPath : SubPage

    @Serializable
    data object UmountFlags : SubPage

    @Serializable
    data object LkmPartition : SubPage

    @Serializable
    data object LkmKmi : SubPage

    @Serializable
    data object SuSFSStatus : SubPage

    @Serializable
    data object SuSFSCapabilities : SubPage

    @Serializable
    data class SuSFSSection(val section: String) : SubPage

    @Serializable
    data class SuSFSAdd(val section: String) : SubPage

    @Serializable
    data class SuSFSDetail(val section: String, val path: String) : SubPage

    @Serializable
    data object SuSFSUname : SubPage

    @Serializable
    data object SuSFSSlots : SubPage

    @Serializable
    data object SuSFSCmdline : SubPage

    @Serializable
    data class SuSFSStandardField(val field: String) : SubPage

    @Serializable
    data class SuSFSEntryField(val index: Int) : SubPage

    @Serializable
    data class SuSFSEntryChoice(val choice: String) : SubPage

    @Serializable
    data object OperationStatus : SubPage

    @Serializable
    data object OperationLog : SubPage

    @Serializable
    data object FilePicker : SubPage

    @Serializable
    data object FileName : SubPage

    @Serializable
    data object WebUI : SubPage

    @Serializable
    data class WebPrompt(val message: String, val defaultValue: String) : SubPage
}

/** A destination that needs a full-featured root manager; it closes when root goes away. */
interface NeedsRoot

/** Destinations sharing the dynamic manager model and manual configuration draft. */
interface DynamicManagerRoute : NeedsRoot

val WearRoute.needsRoot: Boolean
    get() = when (this) {
        is NeedsRoot -> true
        is WearRoute.Operations -> page != WearOperationsPage.Home
        is WearRoute.Settings -> category == WearSettingsCategory.Security || category == WearSettingsCategory.Advanced
        is WearRoute.Selection -> selection == WearSelection.SuCompat
        else -> false
    }
