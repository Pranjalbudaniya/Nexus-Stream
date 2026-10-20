package com.nexus.stream.data.torrent

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkStatus {
    UNMETERED,
    METERED,
    NONE,
}

interface NetworkStatusProvider {
    fun current(): NetworkStatus
}

@Singleton
class ConnectivityNetworkStatusProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkStatusProvider {
        override fun current(): NetworkStatus {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val capabilities = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }

            return when {
                capabilities == null -> NetworkStatus.NONE
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkStatus.UNMETERED
                else -> NetworkStatus.METERED
            }
        }
    }
