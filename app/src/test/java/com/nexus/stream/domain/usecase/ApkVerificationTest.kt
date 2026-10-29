package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.ApkFacts
import com.nexus.stream.domain.model.InstalledFacts
import com.nexus.stream.domain.model.UpdateFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkVerificationTest {
    private val sampleInstalled =
        InstalledFacts(
            packageName = "com.nexus.stream",
            versionCode = 10000L,
            trustsCert = { it == "trusted_cert_sha" },
        )

    @Test
    fun KV1_samePackageHigherCodeTrustedCert_isOk() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10001L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = "sha256hex",
                actualSha256 = "sha256hex",
            )
        assertNull(failure)
    }

    @Test
    fun KV2_differentPackage_failsPackageMismatch() {
        val apk =
            ApkFacts(
                packageName = "com.other",
                versionCode = 10001L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = null,
            )
        assertEquals(UpdateFailure.PackageMismatch, failure)
    }

    @Test
    fun KV3_sameVersionCode_failsNotNewer() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10000L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = null,
            )
        assertEquals(UpdateFailure.NotNewer, failure)
    }

    @Test
    fun KV4_lowerVersionCode_failsNotNewer() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 9999L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = null,
            )
        assertEquals(UpdateFailure.NotNewer, failure)
    }

    @Test
    fun KV5_untrustedCert_failsSignatureMismatch() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10001L,
                signerCertSha256 = setOf("unknown_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = null,
            )
        assertEquals(UpdateFailure.SignatureMismatch, failure)
    }

    @Test
    fun KV6_unreadableArchive_failsInvalidApk() {
        val failure =
            verifyApk(
                apk = null,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = null,
            )
        assertEquals(UpdateFailure.InvalidApk, failure)
    }

    @Test
    fun KV7_checksumMismatch_failsChecksumMismatch() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10001L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = "aa",
                actualSha256 = "bb",
            )
        assertEquals(UpdateFailure.ChecksumMismatch, failure)
    }

    @Test
    fun KV8_checksumAbsent_isSkipped() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10001L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = "actual_sha",
            )
        assertNull(failure)
    }

    @Test
    fun KV9_sizeMismatch_failsSizeMismatch() {
        val apk =
            ApkFacts(
                packageName = "com.nexus.stream",
                versionCode = 10001L,
                signerCertSha256 = setOf("trusted_cert_sha"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 100L,
                actualSize = 90L,
                expectedSha256 = "aa",
                actualSha256 = "bb",
            )
        assertEquals(UpdateFailure.SizeMismatch, failure)
    }

    @Test
    fun KV10_debugPackage_cannotSelfUpdate() {
        assertFalse(canSelfUpdate("com.nexus.stream.debug"))
        assertTrue(canSelfUpdate("com.nexus.stream"))
    }

    @Test
    fun KV11_freeSpace_needsTwiceTheSizePlusMargin() {
        val apkSize = 100L * 1024 * 1024 // 100 MiB
        val margin = 50L * 1024 * 1024 // 50 MiB margin -> total needed: 250 MiB
        val needed = 2 * apkSize + margin
        assertFalse(hasEnoughSpace(needed - 1, apkSize))
        assertTrue(hasEnoughSpace(needed, apkSize))
    }

    @Test
    fun KV12_failureOrder_isStable() {
        val apk =
            ApkFacts(
                packageName = "com.other",
                versionCode = 9999L,
                signerCertSha256 = setOf("unknown_cert"),
            )
        val failure =
            verifyApk(
                apk = apk,
                installed = sampleInstalled,
                expectedSize = 1000L,
                actualSize = 1000L,
                expectedSha256 = null,
                actualSha256 = null,
            )
        assertEquals(UpdateFailure.PackageMismatch, failure)
    }
}
