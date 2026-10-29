package com.nexus.stream.data.update

import android.content.pm.PackageInstaller
import com.nexus.stream.domain.model.UpdateFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStatusMapperTest {
    @Test
    fun IS1_success_mapsToSuccess() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_SUCCESS)
        assertEquals(InstallStatusResult.Success, result)
    }

    @Test
    fun IS2_pendingUserAction_mapsToNeedsConfirmation() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_PENDING_USER_ACTION)
        assertEquals(InstallStatusResult.NeedsConfirmation, result)
    }

    @Test
    fun IS3_aborted_mapsToCancelled() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(InstallStatusResult.Cancelled, result)
    }

    @Test
    fun IS4_conflict_mapsToInstallConflict() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_CONFLICT)
        assertEquals(InstallStatusResult.Failure(UpdateFailure.InstallConflict), result)
    }

    @Test
    fun IS5_storage_mapsToInstallStorage() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_STORAGE)
        assertEquals(InstallStatusResult.Failure(UpdateFailure.InstallStorage), result)
    }

    @Test
    fun IS6_blocked_mapsToInstallBlocked() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_BLOCKED)
        assertEquals(InstallStatusResult.Failure(UpdateFailure.InstallBlocked), result)
    }

    @Test
    fun IS7_incompatible_mapsToInstallIncompatible() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
        assertEquals(InstallStatusResult.Failure(UpdateFailure.InstallIncompatible), result)
    }

    @Test
    fun IS8_invalid_mapsToInstallInvalid() {
        val result = InstallStatusMapper.map(PackageInstaller.STATUS_FAILURE_INVALID)
        assertEquals(InstallStatusResult.Failure(UpdateFailure.InstallInvalid), result)
    }

    @Test
    fun IS9_unknownStatus_mapsToInstallFailed() {
        val result = InstallStatusMapper.map(12345)
        assertEquals(InstallStatusResult.Failure(UpdateFailure.InstallFailed), result)
    }
}
