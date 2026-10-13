package com.nexus.stream.di

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchersModuleTest {
    @Test
    fun provideIoDispatcher_returnsDispatchersIO() {
        assertEquals(Dispatchers.IO, DispatchersModule.provideIoDispatcher())
    }

    @Test
    fun provideDefaultDispatcher_returnsDispatchersDefault() {
        assertEquals(Dispatchers.Default, DispatchersModule.provideDefaultDispatcher())
    }

    @Test
    fun provideMainDispatcher_returnsDispatchersMain() {
        assertEquals(Dispatchers.Main, DispatchersModule.provideMainDispatcher())
    }
}
