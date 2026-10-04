package org.bakasu.bakasu.domain.model

data class ModuleUpdateMetadata(
    val zipUrl: String,
    val version: String,
    val changelog: String,
)
