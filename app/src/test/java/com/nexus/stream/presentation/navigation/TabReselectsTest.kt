package com.nexus.stream.presentation.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TabReselectsTest {
    @Test
    fun TR1_reselectingASelectedTab_emitsThatTabOnce() =
        runTest {
            val tabReselects = TabReselects()
            tabReselects.events.test {
                tabReselects.onTabClick(TopLevelDestination.DISCOVER, isSelected = true)
                assertEquals(TopLevelDestination.DISCOVER, awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun TR2_tappingAnUnselectedTab_emitsNothing() =
        runTest {
            val tabReselects = TabReselects()
            tabReselects.events.test {
                tabReselects.onTabClick(TopLevelDestination.DISCOVER, isSelected = false)
                expectNoEvents()
            }
        }

    @Test
    fun TR3_reselectWithoutCollectors_neitherSuspendsNorThrows() =
        runTest {
            val tabReselects = TabReselects()
            tabReselects.onTabClick(TopLevelDestination.HOME, isSelected = true)
            tabReselects.onTabClick(TopLevelDestination.HOME, isSelected = true)
        }
}
