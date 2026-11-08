package org.bakasu.bakasu.ui.wear.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Error
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.bakasu.bakasu.R
import org.bakasu.bakasu.domain.model.AppProfile
import org.bakasu.bakasu.domain.model.ProfileTemplate
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage

@Composable
fun WearAppProfileModePage(
    profile: AppProfile,
    onBack: () -> Unit,
    onChooseProfile: (AppProfile) -> Unit,
    onChooseTemplate: () -> Unit,
    onChooseCustom: () -> Unit,
) {
    val mode = when {
        profile.allowSu && profile.rootUseDefault -> R.string.profile_default
        profile.allowSu && profile.rootTemplate != null -> R.string.profile_template
        profile.allowSu -> R.string.profile_custom
        profile.nonRootUseDefault -> R.string.profile_default
        else -> R.string.profile_custom
    }
    val choices = listOf(R.string.profile_default, R.string.profile_template, R.string.profile_custom)
        .filter { profile.allowSu || it != R.string.profile_template }
        .map { it.toString() to stringResource(it) }

    WearChoicePage(
        title = stringResource(R.string.profile),
        choices = choices,
        selected = mode.toString(),
        onBack = onBack,
    ) { value ->
        when (value.toInt()) {
            R.string.profile_template -> onChooseTemplate()

            R.string.profile_custom -> onChooseCustom()

            else -> onChooseProfile(
                if (profile.allowSu) {
                    profile.copy(
                        rootUseDefault = value.toInt() == R.string.profile_default,
                        rootTemplate = null,
                    )
                } else {
                    profile.copy(nonRootUseDefault = value.toInt() == R.string.profile_default)
                },
            )
        }
    }
}

@Composable
fun WearAppProfileTemplateChoicePage(
    profile: AppProfile,
    templates: List<ProfileTemplate>,
    isRefreshing: Boolean,
    isLoading: Boolean,
    loadFailed: Boolean,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    onChoose: (ProfileTemplate) -> Unit,
    noMatches: Boolean = false,
) {
    WearChoicePage(
        title = stringResource(R.string.profile_template),
        choices = templates.map { it.id to it.name.ifBlank { it.id } },
        selected = profile.rootTemplate.orEmpty(),
        onBack = onBack,
        isLoading = isLoading,
        isRefreshing = isRefreshing && !isLoading,
        onOpenPanel = onOpenPanel,
        hasOperationsPanel = true,
        labelMaxLines = Int.MAX_VALUE,
        extraContent = { spec ->
            if (loadFailed) {
                item { WearStatusItem(spec, Icons.Rounded.Error, stringResource(R.string.wear_load_failed), tone = WearStatusTone.ERROR) }
            } else if (!isLoading && templates.isEmpty()) {
                item { WearEmptyState(spec, Icons.Rounded.Description, stringResource(if (noMatches) R.string.search_no_any_match else R.string.wear_template_empty)) }
            }
        },
    ) { id ->
        templates.firstOrNull { it.id == id }?.let(onChoose)
    }
}
