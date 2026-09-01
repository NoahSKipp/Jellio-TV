package com.jellio.tv.ui.i18n

import androidx.compose.runtime.Composable
import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.data.prefs.DisplayPreferences

object Strings {
    private val NAMED_PLACEHOLDER = Regex("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}")

    fun tableFor(lang: AppLanguage): Map<String, String> = when (lang) {
        AppLanguage.ENGLISH -> StringsEn.MAP
        AppLanguage.GERMAN -> StringsDe.MAP
        AppLanguage.SPANISH -> StringsEs.MAP
        AppLanguage.FRENCH -> StringsFr.MAP
        AppLanguage.ITALIAN -> StringsIt.MAP
        AppLanguage.POLISH -> StringsPl.MAP
        AppLanguage.PORTUGUESE -> StringsPt.MAP
    }

    private fun mapFor(lang: AppLanguage): Map<String, String> = tableFor(lang)

    fun get(key: String): String {
        val lang = DisplayPreferences.displayLanguage
        val current = tableFor(lang)[key]
        if (current != null) return current
        return StringsEn.MAP[key] ?: key
    }

    fun format(key: String, vararg args: Any): String {
        val template = get(key)
        if (args.isEmpty()) return template
        var result = template
        var usedBracePlaceholder = false
        for (i in args.indices) {
            val token = "{$i}"
            if (result.contains(token)) {
                result = result.replace(token, args[i].toString())
                usedBracePlaceholder = true
            }
        }
        // Replace any {word} named placeholders in order of first appearance.
        val assignedIndices = mutableMapOf<String, Int>()
        var nextIndex = 0
        result = NAMED_PLACEHOLDER.replace(result) { match ->
            usedBracePlaceholder = true
            val tokenName = match.groupValues[1]
            val argIndex = assignedIndices.getOrPut(tokenName) { nextIndex++ }
            if (argIndex < args.size) args[argIndex].toString() else match.value
        }
        if (usedBracePlaceholder && !template.contains('%')) {
            return result
        }
        return runCatching {
            String.format(DisplayPreferences.displayLanguage.locale, result, *args)
        }.getOrDefault(result)
    }
}

@Composable
fun string(key: String): String = Strings.get(key)

@Composable
fun formatString(key: String, vararg args: Any): String = Strings.format(key, *args)
