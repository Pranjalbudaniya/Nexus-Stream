package com.nexus.stream.data.repository

import com.nexus.stream.data.local.db.TransactionRunner
import com.nexus.stream.data.local.db.dao.AddonDao
import com.nexus.stream.data.local.db.entity.AddonEntity
import com.nexus.stream.data.remote.addon.AddonMappers
import com.nexus.stream.data.remote.addon.AddonRemoteDataSource
import com.nexus.stream.data.remote.addon.AddonUrlBuilder
import com.nexus.stream.data.remote.addon.ManifestDto
import com.nexus.stream.di.DefaultDispatcher
import com.nexus.stream.domain.model.Addon
import com.nexus.stream.domain.model.AddonManifest
import com.nexus.stream.domain.model.AddonSource
import com.nexus.stream.domain.model.AppError
import com.nexus.stream.domain.model.Outcome
import com.nexus.stream.domain.repository.AddonRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

class AddonRepositoryImpl(
    private val addonDao: AddonDao,
    private val remoteDataSource: AddonRemoteDataSource,
    private val transactionRunner: TransactionRunner,
    private val json: Json,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    private val clock: () -> Long,
) : AddonRepository {
    @Inject
    constructor(
        addonDao: AddonDao,
        remoteDataSource: AddonRemoteDataSource,
        transactionRunner: TransactionRunner,
        json: Json,
        @DefaultDispatcher dispatcher: CoroutineDispatcher,
    ) : this(
        addonDao = addonDao,
        remoteDataSource = remoteDataSource,
        transactionRunner = transactionRunner,
        json = json,
        dispatcher = dispatcher,
        clock = { System.currentTimeMillis() },
    )

    override fun observeAll(): Flow<List<Addon>> =
        addonDao.observeAll().map { entities ->
            withContext(dispatcher) {
                entities.mapNotNull { it.toDomain(json) }
            }
        }

    override fun observeEnabled(): Flow<List<Addon>> = observeAll().map { addons -> addons.filter { it.enabled } }

    override fun toString(): String = "AddonRepositoryImpl"

    override suspend fun install(transportUrl: String): Outcome<Addon> {
        val normalizedBaseUrl =
            AddonUrlBuilder.normalize(transportUrl)?.toString()
                ?: return Outcome.Failure(AppError.InvalidUrl)

        val fetchOutcome = remoteDataSource.manifestWithRaw(normalizedBaseUrl)
        return when (fetchOutcome) {
            is Outcome.Failure -> {
                fetchOutcome
            }

            is Outcome.Success -> {
                val fetched = fetchOutcome.value
                val manifest = fetched.manifest
                if (manifest.resources.isEmpty()) {
                    Outcome.Failure(AppError.Parse)
                } else {
                    transactionRunner.run {
                        val existing = addonDao.get(normalizedBaseUrl)
                        val entity =
                            if (existing != null) {
                                existing.copy(
                                    id = manifest.id,
                                    name = manifest.name,
                                    version = manifest.version,
                                    manifestJson = fetched.rawJson,
                                )
                            } else {
                                val currentCount = addonDao.getAll().size
                                AddonEntity(
                                    transportUrl = normalizedBaseUrl,
                                    id = manifest.id,
                                    name = manifest.name,
                                    version = manifest.version,
                                    manifestJson = fetched.rawJson,
                                    enabled = true,
                                    position = currentCount,
                                    source = AddonSource.LOCAL,
                                    installedAt = clock(),
                                )
                            }
                        addonDao.upsert(entity)
                        Outcome.Success(
                            Addon(
                                transportUrl = entity.transportUrl,
                                manifest = manifest,
                                enabled = entity.enabled,
                                position = entity.position,
                                source = entity.source,
                                installedAt = entity.installedAt,
                            ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun refresh(transportUrl: String): Outcome<Addon> {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString()
        val isInstalled = normalizedBaseUrl != null && addonDao.get(normalizedBaseUrl) != null
        if (normalizedBaseUrl == null || !isInstalled) {
            val error = if (normalizedBaseUrl == null) AppError.InvalidUrl else AppError.NotFound
            return Outcome.Failure(error)
        }

        return when (val fetchOutcome = remoteDataSource.manifestWithRaw(normalizedBaseUrl)) {
            is Outcome.Failure -> {
                fetchOutcome
            }

            is Outcome.Success -> {
                val fetched = fetchOutcome.value
                val manifest = fetched.manifest
                if (manifest.resources.isEmpty()) {
                    Outcome.Failure(AppError.Parse)
                } else {
                    transactionRunner.run {
                        val current = addonDao.get(normalizedBaseUrl)
                        if (current == null) {
                            Outcome.Failure(AppError.NotFound)
                        } else {
                            val updatedEntity =
                                current.copy(
                                    id = manifest.id,
                                    name = manifest.name,
                                    version = manifest.version,
                                    manifestJson = fetched.rawJson,
                                )
                            addonDao.upsert(updatedEntity)
                            Outcome.Success(
                                Addon(
                                    transportUrl = updatedEntity.transportUrl,
                                    manifest = manifest,
                                    enabled = updatedEntity.enabled,
                                    position = updatedEntity.position,
                                    source = updatedEntity.source,
                                    installedAt = updatedEntity.installedAt,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun fetchManifest(transportUrl: String): Outcome<AddonManifest> {
        val normalizedBaseUrl =
            AddonUrlBuilder.normalize(transportUrl)?.toString()
                ?: return Outcome.Failure(AppError.InvalidUrl)
        return remoteDataSource.manifest(normalizedBaseUrl)
    }

    override suspend fun remove(transportUrl: String) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        transactionRunner.run {
            addonDao.delete(normalizedBaseUrl)
            val remaining = addonDao.getAll()
            val renumbered =
                remaining.mapIndexed { index, entity ->
                    entity.copy(position = index)
                }
            addonDao.updateAll(renumbered)
        }
    }

    override suspend fun setEnabled(
        transportUrl: String,
        enabled: Boolean,
    ) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        addonDao.setEnabled(normalizedBaseUrl, enabled)
    }

    override suspend fun move(
        transportUrl: String,
        toPosition: Int,
    ) {
        val normalizedBaseUrl = AddonUrlBuilder.normalize(transportUrl)?.toString() ?: transportUrl
        transactionRunner.run {
            val all = addonDao.getAll()
            val urls = all.map { it.transportUrl }
            if (normalizedBaseUrl !in urls) return@run

            val newOrder = AddonReorder.reorder(urls, normalizedBaseUrl, toPosition)
            val byUrl = all.associateBy { it.transportUrl }
            val updated =
                newOrder.mapIndexed { index, url ->
                    byUrl.getValue(url).copy(position = index)
                }
            addonDao.updateAll(updated)
        }
    }

    private fun AddonEntity.toDomain(json: Json): Addon? =
        try {
            val dto = json.decodeFromString<ManifestDto>(manifestJson)
            val manifest = AddonMappers.mapManifest(dto, json)
            Addon(
                transportUrl = transportUrl,
                manifest = manifest,
                enabled = enabled,
                position = position,
                source = source,
                installedAt = installedAt,
            )
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
