package org.bakasu.bakasu.domain.model

data class WebUiModuleInfo(
    val name: String,
    val enabled: Boolean,
    val remove: Boolean,
    val hasWebUi: Boolean,
)
