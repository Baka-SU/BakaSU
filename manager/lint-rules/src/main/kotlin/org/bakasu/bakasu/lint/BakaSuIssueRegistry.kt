package org.bakasu.bakasu.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API

class BakaSuIssueRegistry : IssueRegistry() {
    override val issues = listOf(
        SegmentedColumnScopeConditionDetector.ISSUE,
        DirectHorizontalPagerDetector.ISSUE,
    )

    override val api = CURRENT_API
}
