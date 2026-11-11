package com.jellio.tv.data

// When an episode is the first of its season, the seasons to look through
// for the one before it, nearest first. Specials are skipped unless the
// episode is itself a special.
internal fun earlierSeasonIndexes(isSpecials: List<Boolean>, currentIndex: Int): List<Int> {
    if (currentIndex !in isSpecials.indices) return emptyList()
    val currentIsSpecial = isSpecials[currentIndex]
    return (currentIndex - 1 downTo 0).filter { currentIsSpecial || !isSpecials[it] }
}
