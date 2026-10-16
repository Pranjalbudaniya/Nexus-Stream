package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.MetaPreview
import com.nexus.stream.domain.model.RowState
import javax.inject.Inject

class BuildHero
    @Inject
    constructor() {
        operator fun invoke(rows: List<RowState>): List<MetaPreview> = execute(rows)

        companion object {
            private const val MIN_BACKGROUND_ITEMS = 3
            private const val MAX_HERO_ITEMS = 5

            operator fun invoke(rows: List<RowState>): List<MetaPreview> = execute(rows)

            private fun execute(rows: List<RowState>): List<MetaPreview> {
                for (row in rows) {
                    if (row is RowState.Loaded) {
                        val withBackground = row.items.filter { !it.background.isNullOrBlank() }
                        if (withBackground.size >= MIN_BACKGROUND_ITEMS) {
                            return withBackground.take(MAX_HERO_ITEMS)
                        }
                    }
                }
                return emptyList()
            }
        }
    }
