package com.jellio.tv.data.model

import com.jellio.tv.ui.i18n.Strings

// Real port of screens/home.js's own greetingFor()/greetingText: local
// device clock, not the server's own, a real greeting is about the
// reader's own real time of day, not wherever the Jellyfin server
// itself happens to run. 0-4 gets its own real late night text rather
// than folding into "evening", the one bucket real feedback
// specifically called out.
fun greetingFor(hour: Int): String = when {
    hour in 5..11 -> Strings.get("home_greeting_morning")
    hour in 12..16 -> Strings.get("home_greeting_afternoon")
    hour in 17..21 -> Strings.get("home_greeting_evening")
    else -> Strings.get("home_greeting_night")
}

// "Still up" reads as a statement next to a plain name; real feedback
// wanted the late night bucket specifically to read as the direct
// address it actually is, a question rather than a flat greeting.
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
