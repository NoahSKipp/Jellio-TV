package com.jellio.tv.ui.i18n

import androidx.compose.runtime.Composable

// Kept so screens not yet moved to data.i18n still compile.
typealias Strings = com.jellio.tv.data.i18n.Strings

@Composable
fun string(key: String): String = Strings.get(key)

@Composable
fun formatString(key: String, vararg args: Any): String = Strings.format(key, *args)
