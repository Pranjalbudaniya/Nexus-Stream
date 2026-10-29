package com.nexus.stream.data.update

import android.content.pm.PackageInstaller
import com.nexus.stream.domain.model.UpdateFailure

sealed interface InstallStatusResult {
    data object Success : InstallStatusResult

    data object NeedsConfirmation : InstallStatusResult

    data object Cancelled : InstallStatusResult

    data class Failure(
        val failure: UpdateFailure,
    ) : InstallStatusResult
}

object InstallStatusMapper {
    fun map(status: Int): InstallStatusResult =
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                InstallStatusResult.Success
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                InstallStatusResult.NeedsConfirmation
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                InstallStatusResult.Cancelled
            }

            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                InstallStatusResult.Failure(UpdateFailure.InstallConflict)
            }

            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                InstallStatusResult.Failure(UpdateFailure.InstallStorage)
            }

            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                InstallStatusResult.Failure(UpdateFailure.InstallBlocked)
            }

            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> {
                InstallStatusResult.Failure(UpdateFailure.InstallIncompatible)
            }

            PackageInstaller.STATUS_FAILURE_INVALID -> {
                InstallStatusResult.Failure(UpdateFailure.InstallInvalid)
            }

            else -> {
                InstallStatusResult.Failure(UpdateFailure.InstallFailed)
            }
        }
}
