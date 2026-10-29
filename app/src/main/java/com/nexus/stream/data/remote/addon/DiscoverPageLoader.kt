package com.nexus.stream.data.remote.addon

import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.DiscoverCatalog
import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.Outcome
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

const val DISCOVER_TIMEOUT_MS = 8_000L

@Singleton
class DiscoverPageLoader
    @Inject
    constructor(
        private val remote: AddonRemoteDataSource,
    ) {
        suspend fun load(
            catalog: DiscoverCatalog,
            extra: Map<String, String>,
        ): Outcome<List<MetaPreview>> {
            val outcome =
                withTimeoutOrNull(DISCOVER_TIMEOUT_MS) {
                    remote.catalog(
                        transportUrl = catalog.transportUrl,
                        type = catalog.type,
                        catalogId = catalog.catalogId,
                        extra = extra,
                    )
                } ?: return Outcome.Failure(AppError.Timeout)

            return when (outcome) {
                is Outcome.Success -> Outcome.Success(outcome.value.items)
                is Outcome.Failure -> outcome
            }
        }
    }
