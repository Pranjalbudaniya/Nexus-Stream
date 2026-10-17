package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.AddonRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

data class RefreshSummary(
    val updated: Int,
    val failed: List<String>,
)

@Singleton
class RefreshAllAddons
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
    ) {
        private val mutex = Mutex()

        suspend operator fun invoke(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): RefreshSummary {
            if (!mutex.tryLock()) {
                return RefreshSummary(updated = 0, failed = emptyList())
            }
            try {
                return executeRefresh(onProgress)
            } finally {
                mutex.unlock()
            }
        }

        private suspend fun executeRefresh(onProgress: (done: Int, total: Int) -> Unit): RefreshSummary {
            val installed = addonRepository.observeAll().first()
            val total = installed.size
            if (total == 0) {
                return RefreshSummary(updated = 0, failed = emptyList())
            }

            val semaphore = Semaphore(3)
            val completedCount = AtomicInteger(0)
            val failedNames = mutableListOf<String>()
            val lock = Any()

            coroutineScope {
                val jobs =
                    installed.map { addon ->
                        async {
                            semaphore.withPermit {
                                val outcome = addonRepository.refresh(addon.transportUrl)
                                val currentDone = completedCount.incrementAndGet()
                                synchronized(lock) {
                                    if (outcome is Outcome.Failure) {
                                        failedNames.add(addon.manifest.name)
                                    }
                                }
                                onProgress(currentDone, total)
                            }
                        }
                    }
                jobs.awaitAll()
            }

            val failedList = synchronized(lock) { failedNames.toList() }
            val updatedCount = total - failedList.size
            return RefreshSummary(updated = updatedCount, failed = failedList)
        }
    }
