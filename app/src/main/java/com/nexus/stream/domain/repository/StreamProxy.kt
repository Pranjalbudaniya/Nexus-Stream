package com.nexus.stream.domain.repository

import java.util.concurrent.atomic.AtomicBoolean

interface StreamProxy {
    fun open(
        url: String,
        headers: Map<String, String>,
    ): ProxySession
}

class ProxySession(
    val url: String,
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
