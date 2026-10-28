package com.nexus.stream.data.update

import com.nexus.stream.domain.model.UpdateEffect
import com.nexus.stream.domain.model.UpdateState
import com.nexus.stream.domain.repository.UpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultUpdateManager
    @Inject
    constructor() : UpdateManager {
        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle())
        override val state: StateFlow<UpdateState> = _state.asStateFlow()

        private val _effects = MutableSharedFlow<UpdateEffect>()
        override val effects: Flow<UpdateEffect> = _effects.asSharedFlow()

        override fun checkNow() {
            // Staged for Part C implementation
        }

        override fun confirmDownload(allowMetered: Boolean) {
            // Staged for Part C implementation
        }

        override fun cancelDownload() {
            // Staged for Part C implementation
        }

        override fun onInstallPermissionResult() {
            // Staged for Part C implementation
        }

        override fun install() {
            // Staged for Part C implementation
        }

        override fun continueConfirmation() {
            // Staged for Part C implementation
        }

        override fun dismissFailure() {
            // Staged for Part C implementation
        }

        override fun openReleasePage() {
            // Staged for Part C implementation
        }

        override fun onInstallResult(
            status: Int,
            confirmIntent: Any?,
        ) {
            // Staged for Part C implementation
        }

        override fun autoCheckIfDue() {
            // Staged for Part C implementation
        }

        override fun onAppStart() {
            // Staged for Part C implementation
        }
    }
