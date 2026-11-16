package com.jellio.tv.data

import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object SearchQueryNormalizer {

    private val ALIASES = mapOf(
        "lotr" to "The Lord of the Rings",
        "got" to "Game of Thrones",
        "hotd" to "House of the Dragon",
        "aot" to "Attack on Titan",
        "jjk" to "Jujutsu Kaisen",
        "mha" to "My Hero Academia",
        "fmab" to "Fullmetal Alchemist Brotherhood",
        "b99" to "Brooklyn Nine-Nine",
        "himym" to "How I Met Your Mother",
        "tbbt" to "The Big Bang Theory",
        "iasip" to "It's Always Sunny in Philadelphia",
        "sw" to "Star Wars",
        "hp" to "Harry Potter",
    )

    private val COMPOUND_GROUPS = listOf(
        listOf("spiderman", "spider-man", "spider man"),
        listOf("starwars", "star wars"),
        listOf("xmen", "x-men", "x men"),
        listOf("batman", "bat man"),
        listOf("ironman", "iron man"),
    )

    private val NUMBER_VARIANTS = mapOf(
        "99" to listOf("nine-nine", "ninety-nine", "99"),
        "nine-nine" to listOf("99", "nine-nine"),
        "ninety-nine" to listOf("99", "nine-nine"),
        "1" to listOf("one", "i", "1"),
        "one" to listOf("1", "i", "one"),
        "i" to listOf("1", "one", "i"),
        "2" to listOf("two", "ii", "2"),
        "two" to listOf("2", "ii", "two"),
        "ii" to listOf("2", "two", "ii"),
        "3" to listOf("three", "iii", "3"),
        "three" to listOf("3", "iii", "three"),
        "iii" to listOf("3", "three", "iii"),
        "4" to listOf("four", "iv", "4"),
        "four" to listOf("4", "iv", "four"),
        "iv" to listOf("4", "four", "iv"),
        "5" to listOf("five", "v", "5"),
        "five" to listOf("5", "v", "five"),
        "v" to listOf("5", "five", "v"),
        "6" to listOf("six", "vi", "6"),
        "six" to listOf("6", "vi", "six"),
        "vi" to listOf("6", "vi", "six"),
        "7" to listOf("seven", "vii", "7"),
        "seven" to listOf("7", "vii", "seven"),
        "vii" to listOf("7", "seven", "vii"),
        "8" to listOf("eight", "viii", "8"),
        "eight" to listOf("8", "viii", "eight"),
        "viii" to listOf("8", "viii", "eight"),
        "9" to listOf("nine", "ix", "9"),
        "nine" to listOf("9", "ix", "nine"),
        "ix" to listOf("9", "ix", "nine"),
        "10" to listOf("ten", "x", "10"),
        "ten" to listOf("10", "x", "ten"),
        "x" to listOf("10", "ten", "x"),
    )

    private val DIACRITICS_REGEX = "\\p{InCombiningDiacriticalMarks}+".toRegex()
    private val SMART_SINGLE_QUOTES = "[\\u2018\\u2019\\u00B4`]".toRegex()
    private val SMART_DOUBLE_QUOTES = "[\\u201C\\u201D]".toRegex()
    private val PUNCTUATION_REGEX = "[-_.:;,!?()\\[\\]{}#@*\\/\\\\+]".toRegex()
    private val MULTI_SPACE_REGEX = "\\s+".toRegex()

    fun normalize(query: String?): String {
        if (query.isNullOrBlank()) return ""
        val stripped = Normalizer.normalize(query, Normalizer.Form.NFD)
            .replace(DIACRITICS_REGEX, "")
            .replace(SMART_SINGLE_QUOTES, "'")
            .replace(SMART_DOUBLE_QUOTES, "\"")
            .replace("&", " and ")
            .replace("'", "")
            .replace("\"", "")
            .replace(PUNCTUATION_REGEX, " ")
            .lowercase()
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        return stripped
    }

    fun expandVariants(query: String?): List<String> {
        if (query.isNullOrBlank()) return emptyList()
        val raw = query.trim()
        val normalized = normalize(raw)
        val variants = mutableListOf<String>()

        fun add(v: String?) {
            if (v.isNullOrBlank()) return
            val clean = v.trim()
            if (clean.isNotEmpty() && variants.none { it.equals(clean, ignoreCase = true) }) {
                variants.add(clean)
            }
        }

        val lowerRaw = raw.lowercase()
        val aliasMatch = ALIASES[lowerRaw] ?: ALIASES[normalized]
        if (aliasMatch != null) {
            add(aliasMatch)
        }

        for ((key, value) in ALIASES) {
            if (normalize(value) == normalized) {
                add(key)
            }
        }

        add(raw)
        if (normalized != lowerRaw) {
            add(normalized)
        }

        for (group in COMPOUND_GROUPS) {
            val matched = group.any { term ->
                lowerRaw == term || normalized == normalize(term)
            }
            if (matched) {
                for (item in group) {
                    add(item)
                }
            }
        }

        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        var hasNumReplacements = false
        val tokenVariantLists = tokens.map { tok ->
            val numList = NUMBER_VARIANTS[tok]
            if (numList != null) {
                hasNumReplacements = true
                numList
            } else {
                listOf(tok)
            }
        }

        if (hasNumReplacements && tokenVariantLists.size <= 4) {
            fun generateCombos(index: Int, current: List<String>) {
                if (index >= tokenVariantLists.size) {
                    add(current.joinToString(" "))
                    return
                }
                for (opt in tokenVariantLists[index]) {
                    generateCombos(index + 1, current + opt)
                }
            }
            generateCombos(0, emptyList())
        }

        return variants
    }

    fun damerauLevenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        val la = a.length
        val lb = b.length
        val d = Array(la + 1) { IntArray(lb + 1) }

        for (i in 0..la) d[i][0] = i
        for (j in 0..lb) d[0][j] = j

        for (i in 1..la) {
            for (j in 1..lb) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var minVal = min(d[i - 1][j] + 1, d[i][j - 1] + 1)
                minVal = min(minVal, d[i - 1][j - 1] + cost)

                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    minVal = min(minVal, d[i - 2][j - 2] + 1)
                }
                d[i][j] = minVal
            }
        }

        return d[la][lb]
    }

    private fun trigramsOf(str: String): Set<String> {
        val trigrams = mutableSetOf<String>()
        val padded = "  $str "
        for (i in 0 until padded.length - 2) {
            trigrams.add(padded.substring(i, i + 3))
        }
        return trigrams
    }

    private fun trigramSimilarity(a: String, b: String): Double {
        val triA = trigramsOf(a)
        val triB = trigramsOf(b)
        if (triA.isEmpty() || triB.isEmpty()) return 0.0
        var shared = 0
        for (t in triA) {
            if (triB.contains(t)) shared++
        }
        return (2.0 * shared) / (triA.size + triB.size)
    }

    private fun scoreNormalizedPair(q: String, t: String): Double {
        if (q.isEmpty() || t.isEmpty()) return 0.0
        if (q == t) return 1.0
        if (t.startsWith(q)) return 0.95
        if (t.contains(q)) return 0.85

        val tokens = t.split(' ').filter { it.isNotEmpty() }
        val initials = tokens.mapNotNull { it.firstOrNull() }.joinToString("")
        if (initials == q || initials.startsWith(q)) return 0.9

        val maxLen = max(q.length, t.length)
        val dist = damerauLevenshtein(q, t)
        val levScore = max(0.0, 1.0 - dist.toDouble() / maxLen.toDouble())
        val triScore = trigramSimilarity(q, t)
        val score = levScore * 0.6 + triScore * 0.4
        return min(1.0, max(0.0, score))
    }

    fun fuzzyScore(query: String?, candidateTitle: String?): Double {
        if (query.isNullOrBlank() || candidateTitle.isNullOrBlank()) return 0.0
        val t = normalize(candidateTitle)
        if (t.isEmpty()) return 0.0
        val variants = expandVariants(query)
        var best = scoreNormalizedPair(normalize(query), t)
        for (v in variants) {
            val vNorm = normalize(v)
            if (vNorm.isEmpty()) continue
            val s = scoreNormalizedPair(vNorm, t)
            if (s > best) best = s
        }
        return best
    }
}
