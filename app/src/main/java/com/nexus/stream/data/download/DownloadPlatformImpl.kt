package com.nexus.stream.data.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.nexus.stream.data.torrent.NetworkStatus
import com.nexus.stream.domain.repository.DownloadClock
import com.nexus.stream.domain.repository.DownloadNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNetworkImpl
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : DownloadNetwork {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        private val _status = MutableStateFlow(queryCurrentStatus())
        override val status: StateFlow<NetworkStatus> = _status.asStateFlow()

        init {
            val request =
                NetworkRequest
                    .Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

            connectivityManager.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _status.value = queryCurrentStatus()
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities,
                    ) {
                        _status.value = queryStatus(networkCapabilities)
                    }

                    override fun onLost(network: Network) {
                        _status.value = queryCurrentStatus()
                    }
                },
            )
        }

        private fun queryCurrentStatus(): NetworkStatus {
            val active = connectivityManager.activeNetwork
            val caps = active?.let { connectivityManager.getNetworkCapabilities(it) }
            return if (caps != null) queryStatus(caps) else NetworkStatus.NONE
        }

        private fun queryStatus(caps: NetworkCapabilities): NetworkStatus {
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                return NetworkStatus.NONE
            }
            val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            return if (isMetered) NetworkStatus.METERED else NetworkStatus.UNMETERED
        }
    }

@Singleton
class DownloadClockImpl
    @Inject
    constructor() : DownloadClock {
        override fun now(): Long = System.currentTimeMillis()
    }
