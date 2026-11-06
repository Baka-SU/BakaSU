package org.bakasu.bakasu.ui.webui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import org.bakasu.bakasu.R
import org.bakasu.bakasu.data.webui.BridgeActivityLaunch
import org.bakasu.bakasu.domain.model.BridgeAction
import org.bakasu.bakasu.ui.component.rememberCustomDialog
import org.bakasu.bakasu.ui.component.settings.SettingsSwitchWidget
import org.bakasu.bakasu.ui.theme.KernelSUTheme
import org.bakasu.bakasu.ui.viewmodel.CompanionBridgeViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Internal notification/foreground entry. No payload in the Intent can authorize an operation. */
class BridgeRequestActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(BridgeActivityLaunch.REQUEST) ?: run {
            finish()
            return
        }
        setContent {
            KernelSUTheme {
                val viewModel = koinViewModel<CompanionBridgeViewModel>()
                val request by viewModel.request.collectAsStateWithLifecycle()
                val current = request?.takeIf { it.id == id }
                var remember by rememberSaveable { mutableStateOf(false) }
                var approved by rememberSaveable { mutableStateOf(false) }
                var launched by rememberSaveable { mutableStateOf(false) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                fun reject() {
                    viewModel.approve(id, null)
                    viewModel.completed(id, false)
                    finish()
                }
                BackHandler { reject() }
                val dialog = rememberCustomDialog { _ ->
                    AlertDialog(
                        onDismissRequest = ::reject,
                        title = { Text(stringResource(R.string.wear_bridge_request_title)) },
                        text = {
                            Column(Modifier.verticalScroll(rememberScrollState())) {
                                Text(stringResource(R.string.wear_bridge_request_from, current?.peer?.name.orEmpty()))
                                Text(
                                    stringResource(
                                        when (current?.action) {
                                            BridgeAction.WEB_UI -> R.string.wear_bridge_request_webui
                                            BridgeAction.LINK -> R.string.wear_bridge_request_link
                                            else -> R.string.wear_bridge_request_templates
                                        },
                                    ),
                                )
                                Text(if (current?.action == BridgeAction.LINK) current.value else current?.title.orEmpty())
                                SettingsSwitchWidget(
                                    title = stringResource(R.string.wear_bridge_remember),
                                    checked = remember,
                                    onCheckedChange = { remember = it },
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                approved = true
                                viewModel.approve(id, remember)
                            }) { Text(stringResource(R.string.wear_bridge_allow_once)) }
                        },
                        dismissButton = { TextButton(onClick = ::reject) { Text(stringResource(R.string.cancel)) } },
                    )
                }
                LaunchedEffect(current, approved) {
                    if (current == null) {
                        finish()
                        return@LaunchedEffect
                    }
                    viewModel.presented(id)
                    if (!current.requiresConfirmation && !approved) {
                        approved = true
                        viewModel.approve(id, true)
                    }
                    if (current.requiresConfirmation && !approved) dialog.show() else dialog.hide()
                }
                LaunchedEffect(current?.readyToOpen) {
                    val ready = current?.takeIf { it.readyToOpen } ?: return@LaunchedEffect
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        if (!launched) {
                            launched = true
                            val opened = runCatching {
                                when (ready.action) {
                                    BridgeAction.WEB_UI -> startActivity(
                                        Intent(this@BridgeRequestActivity, RemoteWebUIActivity::class.java)
                                            .putExtra(BridgeActivityLaunch.PEER, ready.peer.address)
                                            .putExtra(BridgeActivityLaunch.REQUEST, id),
                                    )

                                    BridgeAction.LINK -> startActivity(Intent(Intent.ACTION_VIEW, ready.value.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))

                                    BridgeAction.TEMPLATES -> Unit
                                }
                            }.isSuccess
                            viewModel.completed(id, opened)
                            if (!opened) BridgeActivityLaunch.failed(id)
                            finish()
                        }
                    }
                }
                if (approved) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}
