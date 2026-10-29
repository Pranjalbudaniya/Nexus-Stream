package com.nexus.stream.presentation.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.stream.domain.model.HomeRowConfig
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.repository.AddonRepository
import com.nexus.stream.domain.repository.HomeRowConfigRepository
import com.nexus.stream.domain.usecase.BuildHomeRows
import com.nexus.stream.domain.usecase.managedRows
import com.nexus.stream.domain.usecase.moveRow
import com.nexus.stream.domain.usecase.setHidden
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagedRowUi(
    val key: String,
    val title: String,
    val hidden: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
)

data class HomeRowsUiState(
    val rows: List<ManagedRowUi> = emptyList(),
    val isDefault: Boolean = true,
    val showResetConfirm: Boolean = false,
    val hiddenCount: Int = 0,
    val totalCount: Int = 0,
)

sealed interface HomeRowsEvent {
    data class MoveUp(
        val key: String,
    ) : HomeRowsEvent

    data class MoveDown(
        val key: String,
    ) : HomeRowsEvent

    data class SetHidden(
        val key: String,
        val hidden: Boolean,
    ) : HomeRowsEvent

    data object RequestReset : HomeRowsEvent

    data object ConfirmReset : HomeRowsEvent

    data object DismissReset : HomeRowsEvent
}

@HiltViewModel
class HomeRowsSettingsViewModel
    @Inject
    constructor(
        private val addonRepository: AddonRepository,
        private val homeRowConfigRepository: HomeRowConfigRepository,
        private val buildHomeRows: BuildHomeRows = BuildHomeRows(),
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(HomeRowsUiState())
        val uiState: StateFlow<HomeRowsUiState> = _uiState.asStateFlow()

        private var currentSpecs: List<HomeRowSpec> = emptyList()

        init {
            viewModelScope.launch {
                combine(
                    addonRepository.observeActive(),
                    homeRowConfigRepository.config,
                ) { active, config ->
                    Pair(active, config)
                }.collect { (active, config) ->
                    val specs = buildHomeRows(active)
                    currentSpecs = specs
                    val managed = managedRows(specs, config)
                    val totalCount = managed.size
                    val hiddenCount = managed.count { it.hidden }
                    val rows =
                        managed.mapIndexed { index, row ->
                            ManagedRowUi(
                                key = row.spec.key,
                                title = row.spec.title,
                                hidden = row.hidden,
                                canMoveUp = index > 0,
                                canMoveDown = index < managed.lastIndex,
                            )
                        }
                    val isDefault = config == HomeRowConfig()
                    _uiState.update {
                        it.copy(
                            rows = rows,
                            isDefault = isDefault,
                            hiddenCount = hiddenCount,
                            totalCount = totalCount,
                        )
                    }
                }
            }
        }

        fun onEvent(event: HomeRowsEvent) {
            when (event) {
                is HomeRowsEvent.MoveUp -> {
                    viewModelScope.launch {
                        homeRowConfigRepository.update { current ->
                            moveRow(currentSpecs, current, event.key, -1)
                        }
                    }
                }

                is HomeRowsEvent.MoveDown -> {
                    viewModelScope.launch {
                        homeRowConfigRepository.update { current ->
                            moveRow(currentSpecs, current, event.key, +1)
                        }
                    }
                }

                is HomeRowsEvent.SetHidden -> {
                    viewModelScope.launch {
                        homeRowConfigRepository.update { current ->
                            setHidden(currentSpecs, current, event.key, event.hidden)
                        }
                    }
                }

                HomeRowsEvent.RequestReset -> {
                    _uiState.update { it.copy(showResetConfirm = true) }
                }

                HomeRowsEvent.ConfirmReset -> {
                    _uiState.update { it.copy(showResetConfirm = false) }
                    viewModelScope.launch {
                        homeRowConfigRepository.reset()
                    }
                }

                HomeRowsEvent.DismissReset -> {
                    _uiState.update { it.copy(showResetConfirm = false) }
                }
            }
        }
    }
