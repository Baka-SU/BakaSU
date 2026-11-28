package org.bakasu.bakasu.domain.model

data class WebUiCommandResult(
    val code: Int,
    val stdout: String,
    val stderr: String,
)
