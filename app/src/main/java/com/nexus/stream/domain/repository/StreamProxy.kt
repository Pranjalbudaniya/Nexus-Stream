package com.nexus.stream.domain.repository

import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class ProxyDiagnostics(
    val lastUpstreamStatus: Int? = null,
    val unsupportedPathRequests: Int = 0,
)

interface StreamProxy {
    fun open(
        url: String,
        headers: Map<String, String>,
    ): ProxySession
}

class ProxySession(
    val url: String,
    val diagnostics: StateFlow<ProxyDiagnostics>,
    private val onClose: () -> Unit,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            onClose()
        }
    }

    override fun toString(): String = "ProxySession"
}
