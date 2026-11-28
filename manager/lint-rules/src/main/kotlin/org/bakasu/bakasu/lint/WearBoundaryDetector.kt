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
import org.jetbrains.uast.UImportStatement

/** Reports cross-imports between phone UI and Wear UI as lint errors. */
class WearBoundaryDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            val imported = node.importReference?.asSourceString() ?: return
            val filePackage = context.uastFile?.packageName ?: return
            val inWear = filePackage == WEAR_PACKAGE || filePackage.startsWith("$WEAR_PACKAGE.") ||
                context.file.invariantSeparatorsPath.contains("/src/wear/")
            val message = when {
                inWear && imported.startsWith("$PHONE_COMPONENT_PACKAGE.") && imported !in SHARED_COMPONENTS ->
                    "Wear UI should not use phone components from $PHONE_COMPONENT_PACKAGE; use $WEAR_COMPONENT_PACKAGE."

                !inWear && imported.startsWith("$WEAR_PACKAGE.") ->
                    "Phone code should not use Wear components from $WEAR_PACKAGE."

                else -> return
            }
            context.report(ISSUE, node, context.getLocation(node), message)
        }
    }

    companion object {
        private const val PHONE_COMPONENT_PACKAGE = "org.bakasu.bakasu.ui.component"
        private const val WEAR_PACKAGE = "org.bakasu.bakasu.ui.wear"
        private const val WEAR_COMPONENT_PACKAGE = "$WEAR_PACKAGE.component"

        // Shared dialog API, app icons and upstream ZIP parsing have no phone-only layout.
        private val SHARED_COMPONENTS = setOf(
            "$PHONE_COMPONENT_PACKAGE.rememberCustomDialog",
            "$PHONE_COMPONENT_PACKAGE.DialogHandle",
            "$PHONE_COMPONENT_PACKAGE.PackageIcon",
            "$PHONE_COMPONENT_PACKAGE.ZipFileDetector",
            "$PHONE_COMPONENT_PACKAGE.ZipFileInfo",
            "$PHONE_COMPONENT_PACKAGE.ZipType",
        )

        val ISSUE: Issue = Issue.create(
            id = "WearPhoneComponentBoundary",
            briefDescription = "Phone and Wear code are mixed",
            explanation = "Phone screens use components from ui.component and Wear screens use components " +
                "from ui.wear.component. Importing across the two keeps watch-specific layouts out of the " +
                "phone UI and the other way around.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                WearBoundaryDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }
}
