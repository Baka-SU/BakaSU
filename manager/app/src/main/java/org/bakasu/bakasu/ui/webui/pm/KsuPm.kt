package org.bakasu.bakasu.ui.webui.pm

import android.webkit.JavascriptInterface
import org.bakasu.bakasu.data.packageinfo.InstalledPackageRepository
import org.json.JSONArray
import org.json.JSONObject

/**
 * Package-manager bridge exposed to the WebUI as `ksu.pm()`.
 *
 * Backed by [InstalledPackageRepository], which is already refreshed by the app's own
 * superuser data flow, so no separate scan is started here.
 */
class KsuPm(private val packageRepository: InstalledPackageRepository) {
    companion object {
        private const val KSU_PM_VERSION = 1
    }

    @JavascriptInterface
    fun version(): Int = KSU_PM_VERSION

    @JavascriptInterface
    fun listPackages(type: String): String {
        val packageNames = packageRepository.packages.value
            .filter { packageInfo ->
                when (type.lowercase()) {
                    "system" -> packageInfo.isSystem
                    "user" -> !packageInfo.isSystem
                    else -> true
                }
            }
            .map { it.packageName }
            .distinct()
            .sorted()

        val jsonArray = JSONArray()
        for (pkgName in packageNames) {
            jsonArray.put(pkgName)
        }
        return jsonArray.toString()
    }

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val appsByPackage = packageRepository.packages.value
            .groupBy { it.packageName }
        val jsonArray = JSONArray()
        for (i in 0 until packageNames.length()) {
            val pkgName = packageNames.getString(i)
            val matches = appsByPackage[pkgName]
            if (matches.isNullOrEmpty()) {
                val error = JSONObject()
                error.put("packageName", pkgName)
                error.put("error", "Package not found or inaccessible")
                jsonArray.put(error)
                continue
            }
            for (pkg in matches.sortedBy { it.uid }) {
                jsonArray.put(buildInfo(pkg))
            }
        }
        return jsonArray.toString()
    }

    private fun buildInfo(pkg: org.bakasu.bakasu.domain.model.InstalledPackageInfo): JSONObject = JSONObject().apply {
        put("packageName", pkg.packageName)
        put("versionName", pkg.versionName)
        put("versionCode", pkg.versionCode)
        put("appLabel", pkg.appLabel)
        put("isSystem", pkg.isSystem)
        put("uid", pkg.uid)
        put("userId", if (pkg.uid >= 0) pkg.uid / 100000 else JSONObject.NULL)
    }
}
