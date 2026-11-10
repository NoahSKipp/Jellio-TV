package com.jellio.tv.data.model

import com.jellio.tv.data.i18n.Strings

fun greetingFor(hour: Int): String = when {
    hour in 5..11 -> Strings.get("home_greeting_morning")
    hour in 12..16 -> Strings.get("home_greeting_afternoon")
    hour in 17..21 -> Strings.get("home_greeting_evening")
    else -> Strings.get("home_greeting_night")
}

fun greetingText(hour: Int, name: String?): String {
    val greeting = greetingFor(hour)
    if (name.isNullOrEmpty()) return greeting
    val question = Regex("\\s?\\?$").find(greeting) ?: return "$greeting, $name"
    return greeting.substring(0, question.range.first) + ", " + name + question.value
}
