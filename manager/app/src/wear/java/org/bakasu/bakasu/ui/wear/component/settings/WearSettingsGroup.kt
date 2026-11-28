package org.bakasu.bakasu.ui.wear.component.settings

import androidx.compose.runtime.Composable
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope

/** Emits group items into the current Wear list without adding a nested scroller. */
@Suppress("ktlint:standard:function-naming")
fun <T> TransformingLazyColumnScope.LazySegmentedColumn(
    entries: List<T>,
    key: (T) -> Any,
    content: @Composable TransformingLazyColumnItemScope.(T) -> Unit,
) {
    items(count = entries.size, key = { key(entries[it]) }) { index -> content(entries[index]) }
}

@Suppress("ktlint:standard:function-naming")
fun <T> TransformingLazyColumnScope.SegmentedColumn(
    entries: List<T>,
    key: (T) -> Any,
    content: @Composable TransformingLazyColumnItemScope.(T) -> Unit,
): Unit = LazySegmentedColumn(entries, key, content)
