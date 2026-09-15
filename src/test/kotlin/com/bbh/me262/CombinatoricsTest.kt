package com.bbh.me262

import com.bbh.me262.util.Combinatorics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CombinatoricsTest {
    @Test fun cartesianProduct() {
        val r = Combinatorics.cartesian(listOf(listOf("a", "b"), listOf("1", "2")), 100)
        assertEquals(listOf(listOf("a", "1"), listOf("a", "2"), listOf("b", "1"), listOf("b", "2")), r)
    }

    @Test fun cartesianRespectsCap() {
        val r = Combinatorics.cartesian(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), 2)
        assertEquals(2, r.size)
    }

    @Test fun cartesianEmptyWhenAnySetEmpty() {
        assertTrue(Combinatorics.cartesian(listOf(listOf("a"), emptyList()), 10).isEmpty())
        assertTrue(Combinatorics.cartesian(emptyList(), 10).isEmpty())
    }

    @Test fun pitchforkZipsToShortest() {
        val r = Combinatorics.pitchfork(listOf(listOf("a", "b", "c"), listOf("1", "2")))
        assertEquals(listOf(listOf("a", "1"), listOf("b", "2")), r)
    }
}
