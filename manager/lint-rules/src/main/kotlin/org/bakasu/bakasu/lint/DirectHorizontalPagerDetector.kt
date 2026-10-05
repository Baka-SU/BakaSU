package org.bakasu.bakasu.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

/** Requires screens to use the app's pager wrapper so gesture handling stays consistent. */
class DirectHorizontalPagerDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitCallExpression(node: UCallExpression) {
            val method = node.resolve() ?: return
            if (method.name != "HorizontalPager") return
            if (method.containingClass?.qualifiedName?.startsWith("androidx.compose.foundation.pager.") != true) return
            if (context.file.name == "HorizontalPagerWithInteraction.kt") return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Do NOT directly calling Compose HorizontalPager, instead, use HorizontalPagerWithInteraction.",
            )
        }
    }

    companion object {

        val ISSUE: Issue = Issue.create(
            id = "DirectHorizontalPager",
            briefDescription = "Direct HorizontalPager usage",
            explanation = "Use HorizontalPagerWithInteraction so pager gesture arbitration remains consistent across the app.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                DirectHorizontalPagerDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
