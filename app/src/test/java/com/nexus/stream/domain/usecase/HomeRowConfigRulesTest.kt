package com.nexus.stream.domain.usecase

import com.nexus.stream.domain.model.HomeRowConfig
import com.nexus.stream.domain.model.HomeRowSpec
import com.nexus.stream.domain.model.MAX_CONFIG_KEYS
import com.nexus.stream.domain.model.decodeHomeRowConfig
import com.nexus.stream.domain.model.encodeHomeRowConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowConfigRulesTest {
    private val specA =
        HomeRowSpec(key = "transport|movie|a", title = "A", transportUrl = "transport", type = "movie", catalogId = "a")
    private val specB =
        HomeRowSpec(key = "transport|movie|b", title = "B", transportUrl = "transport", type = "movie", catalogId = "b")
    private val specC =
        HomeRowSpec(key = "transport|movie|c", title = "C", transportUrl = "transport", type = "movie", catalogId = "c")
    private val specD =
        HomeRowSpec(key = "transport|movie|d", title = "D", transportUrl = "transport", type = "movie", catalogId = "d")
    private val defaultSpecs = listOf(specA, specB, specC, specD)

    @Test
    fun HC1_default_keepsTheSpecOrder() {
        val config = HomeRowConfig()
        val result = applyRowConfig(defaultSpecs, config)
        assertEquals(listOf(specA, specB, specC, specD), result)
    }

    @Test
    fun HC2_orderedKeys_comeFirstInThatOrder() {
        val config = HomeRowConfig(order = listOf(specC.key, specA.key))
        val result = applyRowConfig(defaultSpecs, config)
        assertEquals(listOf(specC, specA, specB, specD), result)
    }

    @Test
    fun HC3_unknownKeys_areIgnored() {
        val config = HomeRowConfig(order = listOf("unknown_X", specC.key, "unknown_Y"))
        val result = applyRowConfig(defaultSpecs, config)
        assertEquals(listOf(specC, specA, specB, specD), result)
    }

    @Test
    fun HC4_newRows_areAppendedAfterOrderedOnes_inDefaultOrder() {
        val specE =
            HomeRowSpec(
                key = "transport|movie|e",
                title = "E",
                transportUrl = "transport",
                type = "movie",
                catalogId = "e",
            )
        val specsWithE = defaultSpecs + specE
        val config = HomeRowConfig(order = listOf(specB.key, specA.key))
        val result = applyRowConfig(specsWithE, config)
        assertEquals(listOf(specB, specA, specC, specD, specE), result)
    }

    @Test
    fun HC5_hiddenKeys_areRemoved() {
        val config = HomeRowConfig(hidden = setOf(specB.key))
        val result = applyRowConfig(defaultSpecs, config)
        assertEquals(listOf(specA, specC, specD), result)
    }

    @Test
    fun HC6_hiddenAndOrdered_applyTogether() {
        val config =
            HomeRowConfig(
                order = listOf(specD.key, specC.key),
                hidden = setOf(specC.key),
            )
        val result = applyRowConfig(defaultSpecs, config)
        assertEquals(listOf(specD, specA, specB), result)
    }

    @Test
    fun HC7_emptyConfig_isIdentity() {
        val config = HomeRowConfig()
        assertEquals(emptyList<HomeRowSpec>(), applyRowConfig(emptyList(), config))
        assertEquals(defaultSpecs, applyRowConfig(defaultSpecs, config))
    }

    @Test
    fun HC8_managedRows_includeHiddenOnes_inOrder() {
        val specB2 =
            HomeRowSpec(
                key = "transport2|movie|b",
                title = "B (addon2)",
                transportUrl = "transport2",
                type = "movie",
                catalogId = "b",
            )
        val specsWithDup = listOf(specA, specB, specB2, specC, specD)
        val config =
            HomeRowConfig(
                order = listOf(specC.key, specA.key),
                hidden = setOf(specA.key),
            )
        val managed = managedRows(specsWithDup, config)
        assertEquals(5, managed.size)
        assertEquals(specC.key, managed[0].spec.key)
        assertFalse(managed[0].hidden)
        assertEquals(specA.key, managed[1].spec.key)
        assertTrue(managed[1].hidden)
        assertEquals(specB.key, managed[2].spec.key)
        assertFalse(managed[2].hidden)
        assertEquals(specB2.key, managed[3].spec.key)
        assertFalse(managed[3].hidden)
        assertEquals(specD.key, managed[4].spec.key)
        assertFalse(managed[4].hidden)
    }

    @Test
    fun HC9_moveUp_swapsWithThePreviousRow() {
        val config = HomeRowConfig()
        val updated = moveRow(defaultSpecs, config, specC.key, -1)
        assertEquals(listOf(specA.key, specC.key, specB.key, specD.key), updated.order)
    }

    @Test
    fun HC10_moveDown_swapsWithTheNextRow() {
        val config = HomeRowConfig()
        val updated = moveRow(defaultSpecs, config, specB.key, +1)
        assertEquals(listOf(specA.key, specC.key, specB.key, specD.key), updated.order)
    }

    @Test
    fun HC11_moveFirstUp_isANoOp() {
        val config = HomeRowConfig()
        val updated = moveRow(defaultSpecs, config, specA.key, -1)
        assertEquals(config, updated)
    }

    @Test
    fun HC12_moveLastDown_isANoOp() {
        val config = HomeRowConfig()
        val updated = moveRow(defaultSpecs, config, specD.key, +1)
        assertEquals(config, updated)
    }

    @Test
    fun HC13_moveUnknownKey_isANoOp() {
        val config = HomeRowConfig()
        val updated = moveRow(defaultSpecs, config, "unknown_X", +1)
        assertEquals(config, updated)
    }

    @Test
    fun HC14_move_storesTheFullCurrentOrder_andPrunesStaleKeys() {
        val config =
            HomeRowConfig(
                order = listOf("stale_Z", specB.key),
                hidden = setOf("stale_Z"),
            )
        val updated = moveRow(defaultSpecs, config, specA.key, +1)
        assertEquals(listOf(specB.key, specC.key, specA.key, specD.key), updated.order)
        assertTrue(updated.hidden.isEmpty())
    }

    @Test
    fun HC15_setHidden_true_addsTheKey_andStoresTheFullOrder() {
        val config = HomeRowConfig()
        val updated = setHidden(defaultSpecs, config, specB.key, true)
        assertEquals(setOf(specB.key), updated.hidden)
        assertEquals(listOf(specA.key, specB.key, specC.key, specD.key), updated.order)
    }

    @Test
    fun HC16_setHidden_false_removesTheKey() {
        val config = HomeRowConfig(hidden = setOf(specB.key, specC.key))
        val updated = setHidden(defaultSpecs, config, specB.key, false)
        assertEquals(setOf(specC.key), updated.hidden)
    }

    @Test
    fun HC17_setHidden_unknownKey_isANoOp() {
        val config = HomeRowConfig()
        val updated = setHidden(defaultSpecs, config, "unknown_X", true)
        assertEquals(config, updated)
    }

    @Test
    fun HC18_defaultConfig_isEmpty() {
        val config = HomeRowConfig()
        assertTrue(config.order.isEmpty())
        assertTrue(config.hidden.isEmpty())
    }

    @Test
    fun HC19_encodeDecode_roundTrips() {
        val config =
            HomeRowConfig(
                order = listOf(specC.key, specA.key),
                hidden = setOf(specB.key),
            )
        val json = encodeHomeRowConfig(config)
        val decoded = decodeHomeRowConfig(json)
        assertEquals(config, decoded)
    }

    @Test
    fun HC20_decode_garbageOrNull_givesTheDefault() {
        val defaultConf = HomeRowConfig()
        assertEquals(defaultConf, decodeHomeRowConfig("{not json"))
        assertEquals(defaultConf, decodeHomeRowConfig(""))
        assertEquals(defaultConf, decodeHomeRowConfig(null))
        assertEquals(defaultConf, decodeHomeRowConfig("[]"))
    }

    @Test
    fun HC21_decode_missingFields_areTolerated() {
        val onlyOrder = decodeHomeRowConfig("""{"order":["keyA"]}""")
        assertEquals(listOf("keyA"), onlyOrder.order)
        assertTrue(onlyOrder.hidden.isEmpty())

        val onlyHidden = decodeHomeRowConfig("""{"hidden":["keyB"]}""")
        assertTrue(onlyHidden.order.isEmpty())
        assertEquals(setOf("keyB"), onlyHidden.hidden)
    }

    @Test
    fun HC22_decode_dropsBlankAndDuplicateKeys_andCaps() {
        val rawJson = """{"order":["A", "", "A", " ", "B"], "hidden":[" "]}"""
        val decoded = decodeHomeRowConfig(rawJson)
        assertEquals(listOf("A", "B"), decoded.order)
        assertTrue(decoded.hidden.isEmpty())

        val manyKeys = (1..600).map { "key_$it" }
        val bigJson = """{"order":${manyKeys.joinToString(prefix = "[\"", separator = "\",\"", postfix = "\"]")}}"""
        val capped = decodeHomeRowConfig(bigJson)
        assertEquals(MAX_CONFIG_KEYS, capped.order.size)
    }

    @Test
    fun HC23_hiddenEverything_appliesToAnEmptyList() {
        val config = HomeRowConfig(hidden = setOf(specA.key, specB.key, specC.key, specD.key))
        val result = applyRowConfig(defaultSpecs, config)
        assertTrue(result.isEmpty())
        val managed = managedRows(defaultSpecs, config)
        assertEquals(4, managed.size)
        assertTrue(managed.all { it.hidden })
    }
}
