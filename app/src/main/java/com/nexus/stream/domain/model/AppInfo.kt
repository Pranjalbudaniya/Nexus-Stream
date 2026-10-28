package com.nexus.stream.domain.model

fun formatAppVersion(
    versionName: String?,
    versionCode: Long,
): String =
    if (versionName.isNullOrBlank()) {
        "$versionCode"
    } else {
        "$versionName ($versionCode)"
    }
