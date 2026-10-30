package org.bakasu.bakasu.ui.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.ui.viewmodel.ModuleRepoViewModel
import org.bakasu.bakasu.ui.wear.component.settings.WearChoicePage

/** Source selection uses the same catalog ViewModel as the repository list. */
@Composable
internal fun WearRepositorySelectionPage(viewModel: ModuleRepoViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WearChoicePage(
        stringResource(R.string.repo_manage),
        state.sources.map { it.url to it.displayName },
        state.repositoryUrl.orEmpty(),
        onBack,
    ) { url ->
        viewModel.selectRepository(url)
        onBack()
    }
}
