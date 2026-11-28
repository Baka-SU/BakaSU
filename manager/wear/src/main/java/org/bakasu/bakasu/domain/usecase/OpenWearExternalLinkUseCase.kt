package org.bakasu.bakasu.domain.usecase

import org.bakasu.bakasu.data.network.WearLinkRepository

class OpenWearExternalLinkUseCase(private val repository: WearLinkRepository) {
    operator fun invoke(url: String) = repository.openExternal(url)
}
