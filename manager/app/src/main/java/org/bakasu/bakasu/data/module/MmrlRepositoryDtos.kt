package org.bakasu.bakasu.data.module

import com.google.gson.annotations.SerializedName

internal data class RepositoryFeedDto(
    @SerializedName("name") val name: String? = null,
    @SerializedName("modules") val modules: List<RepositoryModuleDto?>? = null,
    @SerializedName("metadata") val metadata: RepositoryMetadataDto? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("description") val description: String? = null,
)

internal data class RepositoryDiscoveryDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("id") val id: String? = null,
    @SerializedName("description") val description: String? = null,
)

internal data class RepositoryMetadataDto(@SerializedName("version") val version: Int? = null)

internal data class RepositoryModuleDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("versionCode") val versionCode: Int? = null,
    @SerializedName("versions") val versions: List<RepositoryReleaseDto?>? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("author") val author: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("stars") val stars: Int? = null,
    @SerializedName("readme") val readme: String? = null,
    @SerializedName("timestamp") val timestamp: Double? = null,
    @SerializedName("added") val added: Double? = null,
    @SerializedName("track") val track: RepositoryTrackDto? = null,
)

internal data class RepositoryTrackDto(
    @SerializedName("source") val source: String? = null,
    @SerializedName("added") val added: Double? = null,
)

internal data class RepositoryReleaseDto(
    @SerializedName("versionCode") val versionCode: Int? = null,
    @SerializedName("zipUrl") val zipUrl: String? = null,
    @SerializedName("version") val version: String? = null,
    @SerializedName("timestamp") val timestamp: Double? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("changelog") val changelog: String? = null,
)
