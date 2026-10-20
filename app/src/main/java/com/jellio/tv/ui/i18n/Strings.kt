package com.jellio.tv.ui.i18n

import androidx.compose.runtime.Composable
import com.jellio.tv.data.prefs.AppLanguage
import com.jellio.tv.data.prefs.DisplayPreferences

object Strings {
    private fun mapFor(lang: AppLanguage): Map<String, String> = when (lang) {
        AppLanguage.ENGLISH -> StringsEn.MAP
        AppLanguage.GERMAN -> StringsDe.MAP
        AppLanguage.SPANISH -> StringsEs.MAP
        AppLanguage.FRENCH -> StringsFr.MAP
        AppLanguage.ITALIAN -> StringsIt.MAP
        AppLanguage.POLISH -> StringsPl.MAP
        AppLanguage.PORTUGUESE -> StringsPt.MAP
    }

    fun get(key: String): String {
        val lang = DisplayPreferences.displayLanguage
        val current = mapFor(lang)[key]
        if (current != null) return current
        return StringsEn.MAP[key] ?: key
    }

    fun format(key: String, vararg args: Any): String {
        val template = get(key)
        var result = template
        for (i in args.indices) {
            result = result.replace("{$i}", args[i].toString())
        }
        val namedTokens = listOf("name", "title", "service", "count", "seconds", "genre", "status", "target")
        for (token in namedTokens) {
            if (result.contains("{$token}") && args.isNotEmpty()) {
                result = result.replace("{$token}", args[0].toString())
            }
        }
        return runCatching {
            String.format(result, *args)
        }.getOrDefault(result)
    }
}

@Composable
fun string(key: String): String = Strings.get(key)

@Composable
fun formatString(key: String, vararg args: Any): String = Strings.format(key, *args)
