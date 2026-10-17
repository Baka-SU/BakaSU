package org.bakasu.bakasu.ui.wear.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.foundation.LocalScreenIsActive
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.RevealDirection
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import kotlinx.coroutines.launch
import org.bakasu.bakasu.R

/**
 * A list row that reveals a Delete action when swiped from right to left, with the official
 * [SwipeToReveal] gesture, animation and state. Neither the revealed button nor a full swipe deletes:
 * both cover the row again and call [onDelete], which asks for confirmation. Call it inside an item
 * keyed by the entry, so the reveal state stays with that entry when the list changes. The [content]
 * must not apply the list transformation itself; the row does.
 */
@Composable
fun TransformingLazyColumnItemScope.WearSwipeToDelete(
    transformationSpec: TransformationSpec,
    scrolling: Boolean,
    deleteLabel: String,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberRevealState()
    val scope = rememberCoroutineScope()
    val active = LocalScreenIsActive.current
    // As in the official list sample, a revealed row covers again when the list scrolls.
    LaunchedEffect(scrolling, active) {
        if (!active) {
            state.snapTo(RevealValue.Covered)
        } else if (scrolling && state.currentValue != RevealValue.Covered) {
            state.animateTo(RevealValue.Covered)
        }
    }
    fun requestDelete() {
        scope.launch {
            state.animateTo(RevealValue.Covered)
            onDelete()
        }
    }
    SwipeToReveal(
        revealState = state,
        revealDirection = RevealDirection.RightToLeft,
        primaryAction = {
            PrimaryActionButton(
                onClick = ::requestDelete,
                icon = { Icon(painterResource(R.drawable.wear_symbol_delete), contentDescription = deleteLabel) },
                text = { Text(deleteLabel) },
            )
        },
        onSwipePrimaryAction = ::requestDelete,
        // Wear 1.7.1's SwipeToReveal takes no transformation, so the row applies it as the module
        // swipe rows do, without an offscreen buffer that would clip the revealed action.
        modifier = Modifier.transformedHeight(this, transformationSpec).graphicsLayer {
            with(transformationSpec) { applyContainerTransformation(scrollProgress) }
            compositingStrategy = CompositingStrategy.ModulateAlpha
            clip = false
        }.minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding).semantics {
            customActions = listOf(
                CustomAccessibilityAction(deleteLabel) {
                    requestDelete()
                    true
                },
            )
        },
        content = content,
    )
}
