package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.CINEMETA_URL
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.AppStateRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnsureDefaultAddons
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val appStateRepository: AppStateRepository,
    ) {
        private val mutex = Mutex()

        suspend operator fun invoke(): Outcome<Unit> =
            mutex.withLock {
                val isSeeded = appStateRepository.defaultAddonsSeeded.first()
                if (isSeeded) {
                    return@withLock Outcome.Success(Unit)
                }

                val currentAddons = addonRepository.observeAll().first()
                if (currentAddons.isNotEmpty()) {
                    appStateRepository.markDefaultAddonsSeeded()
                    return@withLock Outcome.Success(Unit)
                }

                when (val result = addonRepository.install(CINEMETA_URL)) {
                    is Outcome.Success -> {
                        appStateRepository.markDefaultAddonsSeeded()
                        Outcome.Success(Unit)
                    }

                    is Outcome.Failure -> {
                        Outcome.Failure(result.error)
                    }
                }
            }
    }
