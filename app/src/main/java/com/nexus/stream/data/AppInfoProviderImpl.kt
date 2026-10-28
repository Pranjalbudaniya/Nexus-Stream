package com.nexus.stream.data

import android.content.Context
import com.nexus.stream.domain.repository.AppInfoProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppInfoProviderImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppInfoProvider {
        @Suppress("DEPRECATION")
        private val packageInfo by lazy {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }.getOrNull()
        }

        override val versionName: String?
            get() = packageInfo?.versionName

        override val versionCode: Long
            get() = packageInfo?.longVersionCode ?: 0L
    }
