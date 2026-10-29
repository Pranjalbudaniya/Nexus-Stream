package com.nexus.stream.data.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import com.nexus.stream.domain.model.ApkFacts
import com.nexus.stream.domain.model.InstalledFacts
import com.nexus.stream.domain.repository.PackageArchiveReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val HEX_RADIX = 16
private const val HEX_BYTE_CHARS = 2

@Singleton
class PackageArchiveReaderImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : PackageArchiveReader {
        override fun readArchiveFacts(file: File): ApkFacts? =
            try {
                extractArchiveFacts(file)
            } catch (_: Exception) {
                null
            }

        private fun extractArchiveFacts(file: File): ApkFacts? {
            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, flags)
            val signatures = getSignatures(archiveInfo?.signingInfo)
            if (archiveInfo == null || signatures == null) return null

            val certs =
                signatures
                    .map { cert ->
                        val md = MessageDigest.getInstance("SHA-256")
                        md.digest(cert.toByteArray()).joinToString("") {
                            java.lang.String.format(java.util.Locale.US, "%02x", it)
                        }
                    }.toSet()

            return ApkFacts(
                packageName = archiveInfo.packageName,
                versionCode = archiveInfo.longVersionCode,
                signerCertSha256 = certs,
            )
        }

        private fun getSignatures(signingInfo: SigningInfo?): Array<Signature>? {
            if (signingInfo == null) return null
            return if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        }

        override fun getInstalledFacts(): InstalledFacts {
            val packageName = context.packageName
            val pm = context.packageManager
            val info = pm.getPackageInfo(packageName, 0)
            val versionCode = info.longVersionCode

            return InstalledFacts(
                packageName = packageName,
                versionCode = versionCode,
                trustsCert = { certShaHex ->
                    try {
                        val certBytes = hexToByteArray(certShaHex)
                        pm.hasSigningCertificate(
                            packageName,
                            certBytes,
                            PackageManager.CERT_INPUT_SHA256,
                        )
                    } catch (_: Exception) {
                        false
                    }
                },
            )
        }

        private fun hexToByteArray(hex: String): ByteArray {
            val clean = hex.trim()
            val bytes = ByteArray(clean.length / HEX_BYTE_CHARS)
            for (i in bytes.indices) {
                val index = i * HEX_BYTE_CHARS
                bytes[i] = clean.substring(index, index + HEX_BYTE_CHARS).toInt(HEX_RADIX).toByte()
            }
            return bytes
        }
    }
