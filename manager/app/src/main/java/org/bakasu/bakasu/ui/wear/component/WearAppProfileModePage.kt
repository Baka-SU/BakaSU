package org.bakasu.bakasu.ui.wear.component

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
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onChoose: (ProfileTemplate) -> Unit,
) {
    WearChoicePage(
        title = stringResource(R.string.profile_template),
        choices = templates.map { it.id to it.name.ifBlank { it.id } },
        selected = profile.rootTemplate.orEmpty(),
        onBack = onBack,
        isLoading = isRefreshing,
        onRefresh = onRefresh,
        labelMaxLines = Int.MAX_VALUE,
    ) { id ->
        templates.firstOrNull { it.id == id }?.let(onChoose)
    }
}
