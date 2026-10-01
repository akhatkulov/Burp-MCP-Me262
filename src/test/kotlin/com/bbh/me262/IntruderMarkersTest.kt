package com.bbh.me262

import com.bbh.me262.util.IntruderMarkers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntruderMarkersTest {

    private fun strip(s: String, marker: String = "§") =
        IntruderMarkers.strip(s.toByteArray(Charsets.UTF_8), marker.toByteArray(Charsets.UTF_8))
            .let { (bytes, ranges) -> String(bytes, Charsets.UTF_8) to ranges }

    @Test fun singlePositionStrippedWithCorrectOffsets() {
        val (out, ranges) = strip("id=§1§")
        assertEquals("id=1", out)
        assertEquals(1, ranges.size)
        // "id=" is 3 bytes, payload "1" occupies [3,4)
        assertEquals(3 to 4, ranges[0])
    }

    @Test fun multiplePositions() {
        val (out, ranges) = strip("a=§x§&b=§y§")
        assertEquals("a=x&b=y", out)
        assertEquals(listOf(2 to 3, 6 to 7), ranges)
    }

    @Test fun noMarkersReturnsEmptyRanges() {
        val (out, ranges) = strip("plain body no markers")
        assertEquals("plain body no markers", out)
        assertTrue(ranges.isEmpty())
    }

    @Test fun emptyPositionIsZeroWidthRange() {
        val (out, ranges) = strip("token=§§")
        assertEquals("token=", out)
        assertEquals(listOf(6 to 6), ranges)
    }

    @Test fun unbalancedMarkersThrow() {
        assertFailsWith<IllegalArgumentException> { strip("id=§1") }
    }

    @Test fun customMultiCharMarker() {
        val (out, ranges) = strip("id=FUZZ1FUZZ", marker = "FUZZ")
        assertEquals("id=1", out)
        assertEquals(listOf(3 to 4), ranges)
    }

    @Test fun offsetsAreByteNotCharForMultibytePayload() {
        // "é" is 2 bytes in UTF-8; offsets must be byte-based.
        val (out, ranges) = strip("x=§é§")
        assertEquals("x=é", out)
        assertEquals(2 to 4, ranges[0])
    }
}
