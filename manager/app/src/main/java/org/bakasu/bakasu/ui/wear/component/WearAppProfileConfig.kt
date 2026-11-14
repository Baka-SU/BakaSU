package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Rule
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.Text
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.profile.Capabilities
import org.bakasu.bakasu.profile.Groups
import org.bakasu.bakasu.toRawFlags
import org.bakasu.bakasu.toRootProfileFlags
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget

/**
 * @author Hanhan_awa
 * @date 2026/10/1.
 */

/** Wear editor for root profile modes and fields; non-root options live in app details. */
@Composable
fun WearAppProfileConfig(
    profile: AppProfile,
    sepolicyValid: Boolean,
    onValidateSepolicy: (String) -> Unit,
    onProfileChange: (AppProfile) -> Unit,
    onManageTemplates: () -> Unit,
    onViewTemplate: (String) -> Unit,
    onBack: () -> Unit,
    templates: org.bakasu.bakasu.ui.viewmodel.TemplateUiState,
    onChooseTemplate: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var choosingTemplateMode by rememberSaveable { mutableStateOf(false) }
    // Only the unsaved choice of template mode is local; saved modes follow the actual profile,
    // including a failed save rolling back in the shared ViewModel.
    val rootMode = when {
        choosingTemplateMode -> R.string.profile_template
        profile.rootUseDefault -> R.string.profile_default
        profile.rootTemplate != null -> R.string.profile_template
        else -> R.string.profile_custom
    }
    var inputError by remember { mutableStateOf(false) }
    var domain by rememberSaveable { mutableStateOf(profile.context) }
    var rules by rememberSaveable { mutableStateOf(profile.rules) }
    var rulesInput by rememberSaveable { mutableStateOf(false) }
    val failed = stringResource(R.string.operation_failed)
    WearResultDialog(if (inputError) WearResult(failed, success = false) else null) { inputError = false }
    val mode = rootMode
    val modeChoices = listOf(R.string.profile_default, R.string.profile_template, R.string.profile_custom)
        .map { it.toString() to stringResource(it) }
    val namespaceChoices = listOf(
        R.string.profile_namespace_inherited,
        R.string.profile_namespace_global,
        R.string.profile_namespace_individual,
    )
        .mapIndexed { index, label -> index.toString() to stringResource(label) }
    val back = {
        page = 0
        inputError = false
    }

    val shownPage = page
    val shownRulesInput = rulesInput
    if (shownPage != 0) {
        WearSubPage(if (shownRulesInput) ({ rulesInput = false }) else back) {
            when (shownPage) {
                R.string.profile -> WearChoicePage(stringResource(R.string.profile), modeChoices, mode.toString(), back) { value ->
                    val selected = value.toInt()
                    choosingTemplateMode = selected == R.string.profile_template
                    // Template mode is persisted only after a template has been selected.
                    if (selected != R.string.profile_template) {
                        onProfileChange(profile.copy(rootUseDefault = selected == R.string.profile_default, rootTemplate = null))
                    }
                    back()
                }

                R.string.profile_namespace -> WearChoicePage(
                    stringResource(shownPage),
                    namespaceChoices,
                    profile.namespace.toString(),
                    back,
                ) {
                    onProfileChange(profile.copy(namespace = it.toInt(), rootUseDefault = false))
                    back()
                }

                R.string.wear_uid, R.string.wear_gid -> {
                    WearTextInputPage(
                        stringResource(shownPage),
                        (if (shownPage == R.string.wear_uid) profile.uid else profile.gid).toString(),
                    ) { value ->
                        val number = value.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.toIntOrNull()
                        if (number == null || number < 0) {
                            back()
                            inputError = true
                        } else {
                            onProfileChange(
                                if (shownPage == R.string.wear_uid) {
                                    profile.copy(uid = number, rootUseDefault = false)
                                } else {
                                    profile.copy(gid = number, rootUseDefault = false)
                                },
                            )
                            back()
                        }
                    }
                }

                R.string.profile_selinux_context -> {
                    if (shownRulesInput) {
                        WearTextInputPage(stringResource(R.string.profile_selinux_rules), rules, true) {
                            rules = it
                            onValidateSepolicy(it)
                            rulesInput = false
                        }
                    } else {
                        WearList(onBack = back) { spec ->
                            item { WearPageHeader(spec, stringResource(shownPage)) }
                            SegmentedColumn(listOf(R.string.profile_selinux_domain, R.string.profile_selinux_rules), { it }) { field ->
                                WearSettingsJumpPageWidget(spec, stringResource(field), {
                                    if (field == R.string.profile_selinux_rules) {
                                        rulesInput = true
                                    } else {
                                        page = R.string.profile_selinux_domain
                                    }
                                }, icon = if (field == R.string.profile_selinux_rules) Icons.Rounded.Rule else Icons.Rounded.Security, description = if (field == R.string.profile_selinux_rules) rules else domain)
                            }
                            item {
                                WearActionButton(spec, Icons.Rounded.Save, stringResource(R.string.app_profile_template_save), {
                                    onProfileChange(profile.copy(context = domain, rules = rules, rootUseDefault = false))
                                    back()
                                }, enabled = domain.matches(Regex("^[a-z_]+:[a-z0-9_]+:[a-z0-9_]+(:[a-z0-9_]+)?$")) && sepolicyValid)
                            }
                        }
                    }
                }

                R.string.profile_selinux_domain -> WearTextInputPage(stringResource(shownPage), domain) {
                    domain = it
                    page = R.string.profile_selinux_context
                }

                else -> WearList(onBack = back) { spec ->
                    item { WearPageHeader(spec, stringResource(shownPage)) }
                    when (shownPage) {
                        R.string.profile_groups -> LazySegmentedColumn(Groups.entries.toList(), { it.gid }) { group ->
                            val selected = profile.groups.ifEmpty { listOf(Natives.ROOT_GID) }
                            WearSettingsSwitchWidget(spec, group.display, group.gid in selected, { checked ->
                                val updated = if (checked) selected + group.gid else selected - group.gid
                                onProfileChange(profile.copy(groups = updated.ifEmpty { listOf(Natives.ROOT_GID) }, rootUseDefault = false))
                            }, enabled = group.gid in selected || selected.size < 32, secondaryLabel = group.gid.toString())
                        }

                        R.string.profile_capabilities -> LazySegmentedColumn(Capabilities.entries.toList(), { it.cap }) { cap ->
                            WearSettingsSwitchWidget(spec, cap.display, cap.cap in profile.capabilities, { checked ->
                                onProfileChange(
                                    profile.copy(
                                        capabilities = if (checked) {
                                            profile.capabilities + cap.cap
                                        } else {
                                            profile.capabilities - cap.cap
                                        },
                                        rootUseDefault = false,
                                    ),
                                )
                            })
                        }

                        R.string.profile_flags -> SegmentedColumn(Natives.Profile.RootProfileFlag.entries.toList(), { it.ordinal }) { flag ->
                            val selected = profile.flags.toRootProfileFlags()
                            WearSettingsSwitchWidget(spec, flag.display, flag in selected, { checked ->
                                onProfileChange(profile.copy(flags = (if (checked) selected + flag else selected - flag).toRawFlags()))
                            }, secondaryLabel = stringResource(flag.desc))
                        }
                    }
                }
            }
        }
    } else {
        WearList(onBack = onBack) { spec ->
            item { WearPageHeader(spec, stringResource(R.string.profile)) }
            if (mode == R.string.profile_template) {
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.profile_template),
                        onChooseTemplate,
                        icon = Icons.Rounded.Bookmarks,
                        description = templates.templateList.firstOrNull { it.id == profile.rootTemplate }?.name ?: profile.rootTemplate,
                    )
                }
                if (profile.rootTemplate != null) {
                    item {
                        WearSettingsJumpPageWidget(
                            spec,
                            stringResource(R.string.app_profile_template_view),
                            { onViewTemplate(profile.rootTemplate) },
                            icon = Icons.Rounded.Description,
                        )
                    }
                }
                item {
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(R.string.manage_app_profile),
                        onManageTemplates,
                        icon = Icons.Rounded.Description,
                    )
                }
            } else if (mode == R.string.profile_custom) {
                val fields = listOf(
                    R.string.wear_uid,
                    R.string.wear_gid,
                    R.string.profile_groups,
                    R.string.profile_capabilities,
                    R.string.profile_namespace,
                    R.string.profile_flags,
                    R.string.profile_selinux_context,
                )
                SegmentedColumn(fields, { it }) { field ->
                    val value = when (field) {
                        R.string.wear_uid -> profile.uid.toString()
                        R.string.wear_gid -> profile.gid.toString()
                        R.string.profile_groups -> profile.groups.joinToString(", ")
                        R.string.profile_capabilities -> profile.capabilities.joinToString(", ")
                        R.string.profile_namespace -> namespaceChoices.getOrNull(profile.namespace)?.second
                        R.string.profile_flags -> profile.flags.toRootProfileFlags().joinToString { it.display }
                        else -> profile.context
                    }
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(field),
                        {
                            if (field == R.string.profile_selinux_context) {
                                domain = profile.context
                                rules = profile.rules
                                onValidateSepolicy(rules)
                            }
                            page = field
                        },
                        icon = when (field) {
                            R.string.wear_uid -> Icons.Rounded.Badge
                            R.string.wear_gid -> Icons.Rounded.Group
                            R.string.profile_groups -> Icons.Rounded.Groups
                            R.string.profile_capabilities -> Icons.Rounded.Extension
                            R.string.profile_namespace -> Icons.Rounded.Category
                            R.string.profile_flags -> Icons.Rounded.Flag
                            else -> Icons.Rounded.Security
                        },
                        description = value,
                    )
                }
            }
        }
    }
}
