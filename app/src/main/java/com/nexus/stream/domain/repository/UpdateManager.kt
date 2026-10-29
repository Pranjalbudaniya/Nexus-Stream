package com.nexus.stream.domain.repository

import com.nexus.stream.domain.model.UpdateEffect
import com.nexus.stream.domain.model.UpdateState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface UpdateManager {
    val state: StateFlow<UpdateState>
    val effects: Flow<UpdateEffect>

    fun checkNow()

    fun confirmDownload(allowMetered: Boolean = false)

    fun cancelDownload()

    fun onInstallPermissionResult()

    fun install()

    fun continueConfirmation()

    fun dismissFailure()

    fun openReleasePage()

    fun onInstallResult(
        status: Int,
        confirmIntent: Any? = null,
    )

    fun autoCheckIfDue()

    fun onAppStart()
}
