package org.bakasu.bakasu.ui.screen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.ExpandLess
import androidx.compose.material.icons.twotone.ExpandMore
import androidx.compose.material.icons.twotone.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.module.registry.DownloadVerificationException
import org.bakasu.bakasu.data.script.ParamError
import org.bakasu.bakasu.data.script.PreparedScript
import org.bakasu.bakasu.data.script.ScriptOutcome
import org.bakasu.bakasu.data.script.ScriptPackageException
import org.bakasu.bakasu.data.script.ScriptParam
import org.bakasu.bakasu.data.script.UnverifiedScriptException
import org.bakasu.bakasu.ui.component.ModuleActionOutput
import org.bakasu.bakasu.ui.component.rememberConfirmDialog
import org.bakasu.bakasu.ui.component.scriptLastRunText
import org.bakasu.bakasu.ui.component.settings.AppBackButton
import org.bakasu.bakasu.ui.component.settings.SegmentedColumn
import org.bakasu.bakasu.ui.component.settings.SettingsBaseWidget
import org.bakasu.bakasu.ui.component.settings.SettingsDropdownWidget
import org.bakasu.bakasu.ui.component.settings.SettingsSwitchWidget
import org.bakasu.bakasu.ui.component.settings.SettingsTextFieldWidget
import org.bakasu.bakasu.ui.component.settings.lazySegmentColumn
import org.bakasu.bakasu.ui.navigation.LocalNavigator
import org.bakasu.bakasu.ui.navigation.Route
import org.bakasu.bakasu.ui.theme.CardConfig
import org.bakasu.bakasu.ui.theme.ThemeConfig
import org.bakasu.bakasu.ui.theme.blurEffect
import org.bakasu.bakasu.ui.theme.blurSource
import org.bakasu.bakasu.ui.util.LocalSnackbarHost
import org.bakasu.bakasu.ui.util.ShellHighlightColors
import org.bakasu.bakasu.ui.util.adaptiveScaffoldWindowInsets
import org.bakasu.bakasu.ui.util.highlightShell
import org.bakasu.bakasu.ui.util.showReplacingSnackbar
import org.bakasu.bakasu.ui.viewmodel.ScriptRunPhase
import org.bakasu.bakasu.ui.viewmodel.ScriptRunUiState
import org.bakasu.bakasu.ui.viewmodel.ScriptRunViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ScriptRunScreen(route: Route.ScriptRun) {
    val viewModel = koinViewModel<ScriptRunViewModel>(key = route.visitId, parameters = { parametersOf(route) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbarHost.current
    val savedMessage = stringResource(R.string.log_saved)
    val running = state.phase == ScriptRunPhase.RUNNING
    val finished = state.phase == ScriptRunPhase.FINISHED
    LaunchedEffect(viewModel) { viewModel.load() }
    DisposableEffect(viewModel) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.leave() }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = running,
        onBackCompleted = {},
    )
    if (running || finished) {
        val outcome = state.exitCode?.let { ScriptOutcome.of(it) }
        ModuleActionOutput(
            output = state.output,
            running = running,
            title = state.prepared?.manifest?.name ?: stringResource(R.string.script_run_title),
            onBack = { if (!running) navigator.pop() },
            onSave = {
                scope.launch {
                    viewModel.saveLog()
                        .onSuccess { snackbar.showReplacingSnackbar("$savedMessage: $it") }
                        .onFailure { snackbar.showReplacingSnackbar(it.message.orEmpty()) }
                }
            },
            result = if (finished) {
                stringResource((outcome ?: ScriptOutcome.FAILURE).resId, state.exitCode ?: -1)
            } else {
                null
            },
            resultColor = when (outcome) {
                ScriptOutcome.SUCCESS -> MaterialTheme.colorScheme.primary
                ScriptOutcome.NOT_APPLICABLE -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.error
            },
        )
        return
    }

    val confirm = rememberConfirmDialog(onConfirm = { viewModel.run() })
    val confirmTitle = stringResource(R.string.script_run_title)
    val confirmContent = stringResource(R.string.script_run_confirm, state.prepared?.manifest?.name.orEmpty())
    val confirmButton = stringResource(R.string.script_run)
    val theme = koinInject<ThemeConfig>()
    val cards = koinInject<CardConfig>()
    val layoutDirection = LocalLayoutDirection.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                modifier = Modifier.blurEffect(),
                title = { Text(stringResource(R.string.script_run_title)) },
                navigationIcon = { AppBackButton(onClick = { navigator.pop() }) },
                windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = 12.dp)),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (theme.isEnableBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(cards.cardAlpha),
                    scrolledContainerColor = if (theme.isEnableBlur) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(cards.cardAlpha),
                ),
            )
        },
        containerColor = Color.Transparent,
        contentWindowInsets = adaptiveScaffoldWindowInsets(),
    ) { padding ->
        if (state.phase == ScriptRunPhase.PREPARING) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoadingIndicator()
                    Text(stringResource(R.string.script_preparing), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else {
            val prepared = state.prepared
            val failure = state.failure
            val paramsTitle = if (prepared?.params?.params.isNullOrEmpty()) null else stringResource(R.string.script_params)
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding().nestedScroll(scrollBehavior.nestedScrollConnection).blurSource(),
                contentPadding = PaddingValues(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding() + 12.dp,
                    end = padding.calculateEndPadding(layoutDirection),
                    bottom = padding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(if (theme.isEnableBlurExp) 0.dp else 8.dp),
            ) {
                if (failure != null) {
                    item {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.script_error_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                            Text(scriptErrorText(failure), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (prepared != null) {
                    lazySegmentColumn(items = listOf(prepared.manifest)) { _, manifest ->
                        SettingsBaseWidget(
                            title = manifest.name,
                            description = listOfNotNull(
                                listOfNotNull(manifest.version.takeIf { it.isNotBlank() }, manifest.versionCode?.let { "($it)" }).joinToString(" ").takeIf { it.isNotBlank() },
                                manifest.author.takeIf { it.isNotBlank() }?.let { "${stringResource(R.string.module_author)}: $it" },
                                manifest.description.takeIf { it.isNotBlank() },
                            ).joinToString("\n"),
                            iconPlaceholder = false,
                        )
                    }
                    lazySegmentColumn(items = listOf(state.lastRun)) { _, last ->
                        SettingsBaseWidget(
                            title = stringResource(R.string.script_notice),
                            titleStyle = MaterialTheme.typography.bodyMedium,
                            description = scriptLastRunText(last),
                            iconPlaceholder = false,
                        )
                    }
                    lazySegmentColumn(items = prepared.params.params, title = paramsTitle, key = { _, param -> param.key }) { _, param ->
                        ScriptParamField(param, state, viewModel::setValue)
                    }
                    item { ScriptSource(prepared) }
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            enabled = state.canRun,
                            onClick = { if (state.canRun) confirm.showConfirm(title = confirmTitle, content = confirmContent, confirm = confirmButton) },
                        ) {
                            Icon(Icons.TwoTone.PlayArrow, contentDescription = null)
                            Text(confirmButton, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                if (route.offerInstall) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.script_local_notice), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { navigator.replace(Route.Flash.modules(listOf(route.uri))) }) {
                                Text(stringResource(R.string.script_install_anyway))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ScriptParamField(param: ScriptParam, state: ScriptRunUiState, onValue: (String, String) -> Unit) {
    val value = state.values[param.key] ?: param.defaultValue
    when (param) {
        is ScriptParam.Bool -> SettingsSwitchWidget(title = param.label, description = param.description, iconPlaceholder = false, checked = value == "true", onCheckedChange = { onValue(param.key, it.toString()) })

        is ScriptParam.Choice -> SettingsDropdownWidget(
            title = param.label,
            description = listOfNotNull(param.description, param.options.firstOrNull { it.value == value }?.let { it.label ?: it.value }).joinToString("\n"),
            iconPlaceholder = false,
            choice = param.options.indexOfFirst { it.value == value }.coerceAtLeast(0),
            data = param.options.map { it.label ?: it.value },
            onChoiceChange = { onValue(param.key, param.options[it].value) },
        )

        is ScriptParam.Text, is ScriptParam.Integer -> {
            val field = rememberTextFieldState(initialText = value)
            LaunchedEffect(field, param.key) { snapshotFlow { field.text.toString() }.collect { onValue(param.key, it) } }
            val error = state.errors[param.key]?.let { paramErrorText(param, it) }
            Column {
                SettingsTextFieldWidget(
                    state = field,
                    title = param.label,
                    error = error.orEmpty(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = if (param is ScriptParam.Integer) KeyboardOptions(keyboardType = if ((param.min ?: Int.MIN_VALUE) < 0) KeyboardType.Ascii else KeyboardType.Number) else KeyboardOptions.Default,
                )
                if (param.description != null && error == null) Text(param.description.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScriptSource(prepared: PreparedScript) {
    var expanded by rememberSaveable(prepared.sha256) { mutableStateOf(false) }
    val colors = ShellHighlightColors.forBackground(MaterialTheme.colorScheme.surfaceBright)
    var highlighted by remember(prepared.sha256, colors) { mutableStateOf<AnnotatedString?>(null) }
    LaunchedEffect(prepared.actionSource, colors, expanded) {
        if (expanded && highlighted == null) {
            highlighted = withContext(Dispatchers.Default) { highlightShell(prepared.actionSource, colors) }
        }
    }
    SegmentedColumn {
        expandableItem(
            expanded = expanded,
            topContent = {
                SettingsBaseWidget(
                    title = stringResource(R.string.script_source),
                    description = if (prepared.actionSourceTruncated) stringResource(R.string.script_source_truncated) else null,
                    iconPlaceholder = false,
                    onClick = { expanded = !expanded },
                ) {
                    Icon(if (expanded) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore, contentDescription = stringResource(if (expanded) R.string.script_collapse_source else R.string.script_expand_source))
                }
            },
        ) {
            item {
                SettingsBaseWidget(
                    title = null,
                    iconPlaceholder = false,
                    descriptionColumnContent = {
                        if (highlighted == null) {
                            if (expanded) LoadingIndicator()
                        } else {
                            SelectionContainer {
                                Text(highlighted!!, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, softWrap = false, modifier = Modifier.horizontalScroll(rememberScrollState()))
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun paramErrorText(param: ScriptParam, error: ParamError): String = when (error) {
    ParamError.TOO_LONG -> stringResource(error.resId, (param as? ScriptParam.Text)?.maxLength ?: 0)
    ParamError.BELOW_MIN -> stringResource(error.resId, (param as? ScriptParam.Integer)?.min ?: 0)
    ParamError.ABOVE_MAX -> stringResource(error.resId, (param as? ScriptParam.Integer)?.max ?: 0)
    else -> stringResource(error.resId)
}

@Composable
private fun scriptErrorText(error: Throwable): String = when (error) {
    is UnverifiedScriptException -> stringResource(R.string.script_error_unverified)

    is DownloadVerificationException -> stringResource(R.string.script_error_verify_failed)

    is ScriptPackageException -> if (error.reason == ScriptPackageException.Reason.INVALID_PARAMS) {
        stringResource(error.reason.resId, error.cause?.message ?: error.message.orEmpty())
    } else {
        stringResource(error.reason.resId)
    }

    else -> stringResource(R.string.script_error_generic, error.message.orEmpty())
}
