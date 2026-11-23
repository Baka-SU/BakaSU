package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import org.bakasu.bakasu.Natives
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.profile.Capabilities
import org.bakasu.bakasu.profile.Groups
import org.bakasu.bakasu.toRawFlags
import org.bakasu.bakasu.toRootProfileFlags
import org.bakasu.bakasu.ui.wear.WearRoute
import org.bakasu.bakasu.ui.wear.component.settings.LazySegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsJumpPageWidget
import org.bakasu.bakasu.ui.wear.component.settings.WearSettingsSwitchWidget
import org.bakasu.bakasu.ui.wear.rememberWearNavigator

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
    var draft by rememberSaveable(profile.name, profile.currentUid) { mutableStateOf(profile) }
    val updateDraft: (AppProfile) -> Unit = { draft = it }
    val navigator = rememberWearNavigator(onExit = onBack, initialRoute = WearRoute.ProfileFields)
    var domain by rememberSaveable { mutableStateOf(draft.context) }
    var rules by rememberSaveable { mutableStateOf(draft.rules) }
    val saveLabel = stringResource(R.string.app_profile_template_save)
    val namespaceChoices = listOf(
        R.string.profile_namespace_inherited,
        R.string.profile_namespace_global,
        R.string.profile_namespace_individual,
    ).mapIndexed { index, label -> index.toString() to stringResource(label) }

    WearSubNavigation(navigator) { route ->
        val back = { if (navigator.top == route) navigator.back() }
        val field = (route as? WearRoute.ProfileField)?.field
        when (field) {
            R.string.wear_uid, R.string.wear_gid -> WearTextInputPage(
                title = stringResource(field),
                initialValue = (if (field == R.string.wear_uid) draft.uid else draft.gid).toString(),
                keyboardType = KeyboardType.Number,
                canSubmit = { it.isNotEmpty() && it.all(Char::isDigit) && (it.toIntOrNull() ?: -1) >= 0 },
            ) { value ->
                updateDraft(
                    if (field == R.string.wear_uid) {
                        draft.copy(uid = value.toInt(), rootUseDefault = false)
                    } else {
                        draft.copy(gid = value.toInt(), rootUseDefault = false)
                    },
                )
                back()
            }

            R.string.profile_namespace -> {
                var selected by rememberSaveable { mutableStateOf(draft.namespace.toString()) }
                WearChoicePage(
                    title = stringResource(field),
                    choices = namespaceChoices,
                    selected = selected,
                    onBack = back,
                    onConfirm = {
                        updateDraft(draft.copy(namespace = selected.toInt(), rootUseDefault = false))
                        back()
                    },
                ) { selected = it }
            }

            R.string.profile_selinux_context -> WearList(
                onBack = back,
                onConfirm = {
                    updateDraft(draft.copy(context = domain, rules = rules, rootUseDefault = false))
                    back()
                },
                confirmEnabled = domain.matches(Regex("^[a-z_]+:[a-z0-9_]+:[a-z0-9_]+(:[a-z0-9_]+)?$")) && sepolicyValid,
            ) { spec ->
                item { WearPageHeader(spec, stringResource(field)) }
                SegmentedColumn(listOf(R.string.profile_selinux_domain, R.string.profile_selinux_rules), { it }) { child ->
                    WearSettingsJumpPageWidget(
                        spec,
                        stringResource(child),
                        { navigator.open(WearRoute.ProfileField(child)) },
                        icon = wearProfileFieldIcon(child),
                        description = if (child == R.string.profile_selinux_rules) rules else domain,
                    )
                }
            }

            R.string.profile_selinux_domain, R.string.profile_selinux_rules -> WearTextInputPage(
                title = stringResource(field),
                initialValue = if (field == R.string.profile_selinux_rules) rules else domain,
                multiline = field == R.string.profile_selinux_rules,
            ) {
                if (field == R.string.profile_selinux_rules) {
                    rules = it
                    onValidateSepolicy(it)
                } else {
                    domain = it
                }
                back()
            }

            R.string.profile_groups, R.string.profile_capabilities, R.string.profile_flags -> {
                var groups by rememberSaveable { mutableStateOf(draft.groups.ifEmpty { listOf(Natives.ROOT_GID) }) }
                var capabilities by rememberSaveable { mutableStateOf(draft.capabilities) }
                var flags by rememberSaveable { mutableStateOf(draft.flags) }
                WearList(
                    onBack = back,
                    onConfirm = {
                        updateDraft(
                            when (field) {
                                R.string.profile_groups -> draft.copy(groups = groups.ifEmpty { listOf(Natives.ROOT_GID) }, rootUseDefault = false)
                                R.string.profile_capabilities -> draft.copy(capabilities = capabilities, rootUseDefault = false)
                                else -> draft.copy(flags = flags, rootUseDefault = false)
                            },
                        )
                        back()
                    },
                ) { spec ->
                    item { WearPageHeader(spec, stringResource(field)) }
                    when (field) {
                        R.string.profile_groups -> LazySegmentedColumn(Groups.entries.toList(), { it.gid }) { group ->
                            WearSettingsSwitchWidget(spec, group.display, group.gid in groups, { checked ->
                                groups = if (checked) groups + group.gid else groups - group.gid
                            }, enabled = group.gid in groups || groups.size < 32, secondaryLabel = group.gid.toString())
                        }

                        R.string.profile_capabilities -> LazySegmentedColumn(Capabilities.entries.toList(), { it.cap }) { cap ->
                            WearSettingsSwitchWidget(spec, cap.display, cap.cap in capabilities, { checked ->
                                capabilities = if (checked) capabilities + cap.cap else capabilities - cap.cap
                            })
                        }

                        R.string.profile_flags -> SegmentedColumn(Natives.Profile.RootProfileFlag.entries.toList(), { it.ordinal }) { flag ->
                            val selected = flags.toRootProfileFlags()
                            WearSettingsSwitchWidget(spec, flag.display, flag in selected, { checked ->
                                flags = (if (checked) selected + flag else selected - flag).toRawFlags()
                            }, secondaryLabel = stringResource(flag.desc))
                        }
                    }
                }
            }

            else -> WearList(
                onBack = onBack,
                onConfirm = { onProfileChange(draft) },
                confirmLabel = saveLabel,
                confirmIcon = Icons.Rounded.Save,
                confirmEnabled = draft != profile,
            ) { spec ->
                item { WearPageHeader(spec, stringResource(R.string.profile)) }
                val templateId = draft.rootTemplate
                if (templateId != null && !draft.rootUseDefault) {
                    item { WearSettingsJumpPageWidget(spec, stringResource(R.string.profile_template), onChooseTemplate, icon = Icons.Rounded.Bookmarks, description = templates.templateList.firstOrNull { it.id == draft.rootTemplate }?.name ?: draft.rootTemplate) }
                    item { WearSettingsJumpPageWidget(spec, stringResource(R.string.app_profile_template_view), { onViewTemplate(templateId) }, icon = Icons.Rounded.Description) }
                    item { WearSettingsJumpPageWidget(spec, stringResource(R.string.manage_app_profile), onManageTemplates, icon = Icons.Rounded.Description) }
                } else if (!draft.rootUseDefault) {
                    val fields = listOf(R.string.wear_uid, R.string.wear_gid, R.string.profile_groups, R.string.profile_capabilities, R.string.profile_namespace, R.string.profile_flags, R.string.profile_selinux_context)
                    SegmentedColumn(fields, { it }) { child ->
                        val value = when (child) {
                            R.string.wear_uid -> draft.uid.toString()
                            R.string.wear_gid -> draft.gid.toString()
                            R.string.profile_groups -> draft.groups.joinToString(", ")
                            R.string.profile_capabilities -> draft.capabilities.joinToString(", ")
                            R.string.profile_namespace -> namespaceChoices.getOrNull(draft.namespace)?.second
                            R.string.profile_flags -> draft.flags.toRootProfileFlags().joinToString { it.display }
                            else -> draft.context
                        }
                        WearSettingsJumpPageWidget(spec, stringResource(child), {
                            if (child == R.string.profile_selinux_context) {
                                domain = draft.context
                                rules = draft.rules
                                onValidateSepolicy(rules)
                            }
                            navigator.open(WearRoute.ProfileField(child))
                        }, icon = wearProfileFieldIcon(child), description = value)
                    }
                }
            }
        }
    }
}
