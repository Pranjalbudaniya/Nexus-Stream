package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.Decision
import com.nexus.stream.domain.model.ReleaseAsset
import com.nexus.stream.domain.model.ReleaseInfo
import com.nexus.stream.domain.model.UpdateCandidate
import com.nexus.stream.domain.model.UpdateDecision
import com.nexus.stream.domain.model.UpdateFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseRulesTest {
    @Test
    fun RR1_pickAsset_singleApk() {
        val apk = ReleaseAsset("app.apk", 1000L, "https://github.com/a/b/app.apk")
        val txt = ReleaseAsset("notes.txt", 200L, "https://github.com/a/b/notes.txt")
        val picked = pickApkAsset(listOf(apk, txt), listOf("arm64-v8a"))
        assertEquals(apk, picked)
    }

    @Test
    fun RR2_pickAsset_prefersTheMatchingAbi() {
        val arm64 = ReleaseAsset("x-arm64-v8a.apk", 1000L, "https://github.com/a/b/arm64.apk")
        val x86 = ReleaseAsset("x-x86_64.apk", 1000L, "https://github.com/a/b/x86.apk")
        val picked = pickApkAsset(listOf(arm64, x86), listOf("arm64-v8a", "armeabi-v7a"))
        assertEquals(arm64, picked)
    }

    @Test
    fun RR3_pickAsset_universalWhenNoAbiMatch() {
        val universal = ReleaseAsset("x-universal.apk", 1000L, "https://github.com/a/b/uni.apk")
        val x86 = ReleaseAsset("x-x86_64.apk", 1000L, "https://github.com/a/b/x86.apk")
        val picked = pickApkAsset(listOf(universal, x86), listOf("arm64-v8a"))
        assertEquals(universal, picked)
    }

    @Test
    fun RR4_pickAsset_ignoresNonApkFiles() {
        val aab = ReleaseAsset("app.aab", 1000L, "https://github.com/a/b/app.aab")
        val zip = ReleaseAsset("app.zip", 1000L, "https://github.com/a/b/app.zip")
        val sha = ReleaseAsset("app.sha256", 64L, "https://github.com/a/b/app.sha256")
        val picked = pickApkAsset(listOf(aab, zip, sha), listOf("arm64-v8a"))
        assertNull(picked)
    }

    @Test
    fun RR5_pickAsset_ambiguousApks_isNull() {
        val apk1 = ReleaseAsset("app1.apk", 1000L, "https://github.com/a/b/app1.apk")
        val apk2 = ReleaseAsset("app2.apk", 1000L, "https://github.com/a/b/app2.apk")
        val picked = pickApkAsset(listOf(apk1, apk2), listOf("arm64-v8a"))
        assertNull(picked)
    }

    @Test
    fun RR6_pickAsset_noAssets_isNull() {
        val picked = pickApkAsset(emptyList(), listOf("arm64-v8a"))
        assertNull(picked)
    }

    @Test
    fun RR7_pickAsset_extensionIsCaseInsensitive() {
        val apk = ReleaseAsset("NexusStream.APK", 1000L, "https://github.com/a/b/app.apk")
        val picked = pickApkAsset(listOf(apk), listOf("arm64-v8a"))
        assertEquals(apk, picked)
    }

    @Test
    fun RR8_allowedUrl_githubAndGithubusercontent() {
        assertTrue(isAllowedDownloadUrl("https://github.com/a/b/releases/download/v1/x.apk"))
        assertTrue(isAllowedDownloadUrl("https://objects.githubusercontent.com/x"))
        assertTrue(isAllowedDownloadUrl("https://release-assets.githubusercontent.com/x"))
    }

    @Test
    fun RR9_allowedUrl_rejectsHttpOtherHostsAndLookalikes() {
        assertFalse(isAllowedDownloadUrl("http://github.com/x"))
        assertFalse(isAllowedDownloadUrl("https://github.com.evil.com/x"))
        assertFalse(isAllowedDownloadUrl("https://evilgithubusercontent.com/x"))
        assertFalse(isAllowedDownloadUrl("https://user@github.com/x"))
        assertFalse(isAllowedDownloadUrl("https://example.com/x"))
    }

    @Test
    fun RR10_notes_stripMarkdownBasics() {
        val markdown = "## New\n**Fast** `code` [site](https://x)\n- item"
        val stripped = releaseNotesText(markdown)
        assertEquals("New\nFast code site\n• item", stripped)
    }

    @Test
    fun RR11_notes_areCappedWithEllipsis() {
        val longText = "a".repeat(3000)
        val capped = releaseNotesText(longText, 1500)
        assertNotNull(capped)
        assertEquals(1500, capped!!.length)
        assertTrue(capped.endsWith("…"))
    }

    @Test
    fun RR12_notes_blank_isNull() {
        assertNull(releaseNotesText(null))
        assertNull(releaseNotesText("  \n "))
    }

    @Test
    fun RR13_decide_newerRelease_isAvailable() {
        val apk = ReleaseAsset("x-arm64-v8a.apk", 1000L, "https://github.com/a/b/x.apk")
        val release =
            ReleaseInfo(
                tag = "v1.0.1",
                title = "Version 1.0.1",
                notes = "Fixes",
                htmlUrl = "https://github.com/a/b/releases/tag/v1.0.1",
                assets = listOf(apk),
            )
        val decision = decideUpdate("1.0.0", release, listOf("arm64-v8a"))
        assertTrue(decision is Decision.Available)
        val candidate = (decision as Decision.Available).candidate
        assertEquals(apk, candidate.asset)
        assertEquals("v1.0.1", candidate.tag)
    }

    @Test
    fun RR14_decide_sameOrOlder_isUpToDate() {
        val apk = ReleaseAsset("x-arm64-v8a.apk", 1000L, "https://github.com/a/b/x.apk")
        val relSame =
            ReleaseInfo(
                tag = "v1.0.0",
                title = "1.0.0",
                htmlUrl = "https://github.com/a/b",
                assets = listOf(apk),
            )
        val relOlder =
            ReleaseInfo(
                tag = "v0.9.0",
                title = "0.9.0",
                htmlUrl = "https://github.com/a/b",
                assets = listOf(apk),
            )
        assertEquals(Decision.UpToDate, decideUpdate("1.0.0", relSame, listOf("arm64-v8a")))
        assertEquals(Decision.UpToDate, decideUpdate("1.0.0", relOlder, listOf("arm64-v8a")))
    }

    @Test
    fun RR15_decide_newerWithoutApk_failsNoApk() {
        val release =
            ReleaseInfo(
                tag = "v1.0.1",
                title = "1.0.1",
                htmlUrl = "https://github.com/a/b",
                assets = emptyList(),
            )
        val decision = decideUpdate("1.0.0", release, listOf("arm64-v8a"))
        assertEquals(Decision.Failure(UpdateFailure.NoApk), decision)
    }

    @Test
    fun RR16_decide_unparseableTag_failsBadResponse() {
        val release =
            ReleaseInfo(
                tag = "latest",
                title = "latest",
                htmlUrl = "https://github.com/a/b",
                assets = emptyList(),
            )
        val decision = decideUpdate("1.0.0", release, listOf("arm64-v8a"))
        assertEquals(Decision.Failure(UpdateFailure.BadResponse), decision)
    }

    @Test
    fun RR17_decide_draftOrPrerelease_isIgnored() {
        val apk = ReleaseAsset("x-arm64-v8a.apk", 1000L, "https://github.com/a/b/x.apk")
        val prerelease =
            ReleaseInfo(
                tag = "v1.0.1",
                title = "1.0.1",
                htmlUrl = "https://github.com/a/b",
                isPrerelease = true,
                assets = listOf(apk),
            )
        val draft =
            ReleaseInfo(
                tag = "v1.0.1",
                title = "1.0.1",
                htmlUrl = "https://github.com/a/b",
                isDraft = true,
                assets = listOf(apk),
            )
        assertEquals(Decision.UpToDate, decideUpdate("1.0.0", prerelease, listOf("arm64-v8a")))
        assertEquals(Decision.UpToDate, decideUpdate("1.0.0", draft, listOf("arm64-v8a")))
    }

    @Test
    fun RR18_digest_parsesSha256Only() {
        assertEquals("abc123", parseDigest("sha256:ABC123"))
        assertNull(parseDigest("sha1:abc"))
        assertNull(parseDigest(null))
        assertNull(parseDigest("sha256:"))
    }
}
