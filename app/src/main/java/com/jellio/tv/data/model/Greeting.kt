package com.jellio.tv.data.model

// Real port of screens/home.js's own greetingFor()/greetingText: local
// device clock, not the server's own, a real greeting is about the
// reader's own real time of day, not wherever the Jellyfin server
// itself happens to run. 0-4 gets its own real late night text rather
// than folding into "evening", the one bucket real feedback
// specifically called out.
fun greetingFor(hour: Int): String = when {
    hour in 5..11 -> "Good morning"
    hour in 12..16 -> "Good afternoon"
    hour in 17..21 -> "Good evening"
    else -> "Still up"
}

// "Still up" reads as a statement next to a plain name; real feedback
// wanted the late night bucket specifically to read as the direct
// address it actually is, a question rather than a flat greeting.
fun greetingText(hour: Int, name: String?): String {
    val greeting = greetingFor(hour)
    val suffix = if (greeting == "Still up") "?" else ""
    return if (!name.isNullOrEmpty()) "$greeting, $name$suffix" else "$greeting$suffix"
}
