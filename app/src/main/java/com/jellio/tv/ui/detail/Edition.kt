package com.jellio.tv.ui.detail

import com.jellio.tv.data.model.MediaSourceDto

data class EditionGroup(
    val name: String,
    val isDefault: Boolean,
    val sources: List<MediaSourceDto>,
)

object EditionParser {
    val MONOCHROME_REGEX = Regex(
        """\b(noir|black[._\s]*(?:and|&)[._\s]*white|b[&/]w|black[._\s]*(?:and|&)[._\s]*chrome|minus[._\s]*color|justice[._\s]*is[._\s]*gray|in[._\s]*black[._\s]*(?:and|&)[._\s]*white)\b""",
        RegexOption.IGNORE_CASE,
    )

    val CUT_PATTERNS = listOf(
        "Final Cut" to Regex("""\b(final[._\s]*cut)\b""", RegexOption.IGNORE_CASE),
        "Director's Cut" to Regex("""\b(director(?:'s|[._\s]*s)?[._\s]*cut|\bdc\b)""", RegexOption.IGNORE_CASE),
        "Extended" to Regex("""\b(extended(?:[._\s]*(?:cut|edition))?|special[._\s]*edition)\b""", RegexOption.IGNORE_CASE),
        "Theatrical" to Regex("""\b(theatrical(?:[._\s]*(?:cut|edition))?)\b""", RegexOption.IGNORE_CASE),
        "Unrated" to Regex("""\b(unrated|uncut)\b""", RegexOption.IGNORE_CASE),
        "Redux" to Regex("""\b(redux)\b""", RegexOption.IGNORE_CASE),
        "IMAX" to Regex("""\b(imax(?:[._\s]*enhanced)?)\b""", RegexOption.IGNORE_CASE),
    )

    fun sourceText(source: MediaSourceDto?): String {
        if (source == null) return ""
        return "${source.Name ?: ""} ${source.Path ?: ""}".trim()
    }

    fun rawEditionTag(source: MediaSourceDto?): String? {
        val text = sourceText(source)
        if (text.isBlank()) return null
        if (MONOCHROME_REGEX.containsMatchIn(text)) {
            return "Noir / B&W"
        }
        for ((name, regex) in CUT_PATTERNS) {
            if (regex.containsMatchIn(text)) {
                return name
            }
        }
        return null
    }

    fun getAvailableEditions(sources: List<MediaSourceDto>?): List<String> {
        if (sources.isNullOrEmpty()) return emptyList()

        val tags = sources.map { rawEditionTag(it) }
        val hasMonochrome = tags.any { it == "Noir / B&W" }
        val hasNonMonochrome = tags.any { it != "Noir / B&W" }

        val editions = mutableListOf<String>()

        if (hasMonochrome && hasNonMonochrome) {
            editions.add("Color")
            editions.add("Noir / B&W")
        } else {
            val distinctCuts = tags.filter { it != null && it != "Noir / B&W" }.toSet()
            val hasUncut = tags.any { it == null }
            if (distinctCuts.isNotEmpty() && (hasUncut || distinctCuts.size > 1)) {
                if (hasUncut || distinctCuts.contains("Theatrical")) {
                    editions.add("Theatrical")
                }
                val order = listOf("Extended", "Director's Cut", "Final Cut", "Unrated", "Redux", "IMAX")
                for (cut in order) {
                    if (distinctCuts.contains(cut)) {
                        editions.add(cut)
                    }
                }
            }
        }

        if (editions.size < 2) return emptyList()
        return editions
    }

    fun detectSourceEdition(source: MediaSourceDto?, allSources: List<MediaSourceDto>?): String {
        if (source == null) return "Standard"
        val sources = if (!allSources.isNullOrEmpty()) allSources else listOf(source)
        val editions = getAvailableEditions(sources)
        if (editions.size < 2) {
            return rawEditionTag(source) ?: "Standard"
        }

        val tag = rawEditionTag(source)

        if (editions.contains("Noir / B&W") && editions.contains("Color")) {
            return if (tag == "Noir / B&W") "Noir / B&W" else "Color"
        }

        if (tag != null && editions.contains(tag)) {
            return tag
        }

        if (editions.contains("Theatrical")) {
            return "Theatrical"
        }

        return editions[0]
    }

    fun groupSourcesByEdition(sources: List<MediaSourceDto>?): List<EditionGroup> {
        if (sources.isNullOrEmpty()) return emptyList()
        val editions = getAvailableEditions(sources)
        if (editions.size < 2) {
            return listOf(EditionGroup(name = "Standard", isDefault = true, sources = sources))
        }

        val map = linkedMapOf<String, MutableList<MediaSourceDto>>()
        for (ed in editions) {
            map[ed] = mutableListOf()
        }
        for (source in sources) {
            val ed = detectSourceEdition(source, sources)
            if (map.containsKey(ed)) {
                map[ed]!!.add(source)
            } else {
                map[editions[0]]!!.add(source)
            }
        }

        return editions.mapIndexed { index, ed ->
            EditionGroup(
                name = ed,
                isDefault = index == 0,
                sources = map[ed] ?: emptyList(),
            )
        }
    }
}
