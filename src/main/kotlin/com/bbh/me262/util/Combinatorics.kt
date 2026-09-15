package com.bbh.me262.util

/** Pure payload-combination logic for the fuzzer (no Burp deps, unit-tested). */
object Combinatorics {
    /** Cartesian product of the sets, lazily generated and capped to [cap]. */
    fun cartesian(sets: List<List<String>>, cap: Int): List<List<String>> {
        if (sets.isEmpty() || sets.any { it.isEmpty() }) return emptyList()
        var acc: Sequence<List<String>> = sequenceOf(emptyList())
        for (set in sets) acc = acc.flatMap { prefix -> set.asSequence().map { prefix + it } }
        return acc.take(cap).toList()
    }

    /** Parallel iteration (zip), stopping at the shortest set. */
    fun pitchfork(sets: List<List<String>>): List<List<String>> {
        if (sets.isEmpty() || sets.any { it.isEmpty() }) return emptyList()
        val n = sets.minOf { it.size }
        return (0 until n).map { i -> sets.map { it[i] } }
    }
}
