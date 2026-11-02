package com.jellio.tv.data.model

import com.jellio.tv.ui.i18n.Strings

fun greetingFor(hour: Int): String = when {
    hour in 5..11 -> Strings.get("home_greeting_morning")
    hour in 12..16 -> Strings.get("home_greeting_afternoon")
    hour in 17..21 -> Strings.get("home_greeting_evening")
    else -> Strings.get("home_greeting_night")
}

fun greetingText(hour: Int, name: String?): String {
    val greeting = greetingFor(hour)
    if (name.isNullOrEmpty()) return greeting
    val night = Strings.get("home_greeting_night")
    return if (greeting == night) {
        if (greeting.endsWith("?")) "$name, $greeting" else "$greeting, $name?"
    } else {
        "$greeting, $name"
    }
}
