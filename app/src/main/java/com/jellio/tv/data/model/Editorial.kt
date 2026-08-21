package com.jellio.tv.data.model

import java.util.Calendar

// Time-of-day copy table for the Shows library's own coverflow header.
// Ported faithfully from runtime/editorial.js's own showsEditorial()
// (itself ported from Harbor's own src/views/shows/hero-curation.ts:
// dayBucket(), the real BUCKET_VARIANTS copy table, and bucketCopy()'s
// own day-of-year rotation formula), not a single-variant-per-bucket
// simplification: real Harbor rotates through 7 real copy variants
// per bucket, picking one by day of year rather than holding one line
// fixed all bucket long.

data class ShowsEditorial(val icon: String, val label: String, val tagline: String, val description: String)

private data class BucketCopy(val kicker: String, val title: String, val subtitle: String)

private val BUCKET_ICONS = mapOf(
    "morning" to "wb_sunny",
    "afternoon" to "wb_cloudy",
    "evening" to "weekend",
    "night" to "bedtime",
)

private val BUCKET_INDEX = mapOf("morning" to 0, "afternoon" to 1, "evening" to 2, "night" to 3)

private val BUCKET_VARIANTS = mapOf(
    "morning" to listOf(
        BucketCopy("Morning Lineup", "Easing into series", "Slow-burn worlds and bright chapters worth opening with coffee."),
        BucketCopy("Good Morning", "Today's openers", "Series to ease into while the day is still quiet."),
        BucketCopy("Daybreak", "First-light picks", "Worlds to step into before the inbox catches up."),
        BucketCopy("AM Picks", "Coffee-and-couch", "Half-hours, anthologies, and a few epics for the morning routine."),
        BucketCopy("Open the Day", "Series with mileage", "Long-running comforts and new chapters worth pressing play on."),
        BucketCopy("Quiet Hours", "Slow-burn starts", "Stories that reward your attention before the day gets loud."),
        BucketCopy("This Morning", "Worth catching up on", "What everyone has been quietly binging this week."),
    ),
    "afternoon" to listOf(
        BucketCopy("Afternoon Picks", "Daytime watching", "Easy half-hours and lighter dramas to ride out the afternoon."),
        BucketCopy("Midday Lineup", "Between meetings", "Episodes you can drop into without losing the thread."),
        BucketCopy("Afternoon Roll", "Pick up an episode", "Lunch-break comedies and slow-cooker dramas, ready when you are."),
        BucketCopy("The Long Lunch", "Series to disappear into", "Worlds wide enough for an hour or a whole free afternoon."),
        BucketCopy("Daylight Watching", "Bright-side series", "Sharp comedies, sunny worlds, and the occasional binge bait."),
        BucketCopy("Holdover Picks", "Carry it through the day", "Companion series for whatever the afternoon throws at you."),
        BucketCopy("PM Picks", "Couch hours", "Series for the part of the day that runs on coffee and snacks."),
    ),
    "evening" to listOf(
        BucketCopy("Tonight", "Tonight's lineup", "Prestige drama, weekly chapters, and series worth disappearing into."),
        BucketCopy("Prime Time", "What to watch tonight", "Crowd-pleasers, prestige picks, and the kind of series people text about."),
        BucketCopy("Sundown", "Evening on the couch", "Drop-in chapters and long arcs for the post-dinner stretch."),
        BucketCopy("Press Play", "Tonight's marquee", "The series that make the rest of the night disappear."),
        BucketCopy("Tonight's Slate", "Episodes worth the evening", "What's hot this week, what's prestige forever, what's worth the hours."),
        BucketCopy("Showtime", "Tonight's main event", "Series for the part of the day you actually look forward to."),
        BucketCopy("Saved for Now", "Tonight's binge bait", "Pilots that pull you in and finales that earn the season."),
    ),
    "night" to listOf(
        BucketCopy("Late Night", "After-hours picks", "Dark, immersive, and binge-worthy when the house is quiet."),
        BucketCopy("Past Midnight", "One more episode", "Series for the part of the night that won't let you sleep."),
        BucketCopy("Witching Hour", "Late-night chapters", "Pull-you-under stories for the quietest part of the day."),
        BucketCopy("Lights Out", "Headphone series", "Slow, strange, and absorbing. Best with the lights down low."),
        BucketCopy("Insomnia Lineup", "Worth the lost hour", "Dense plots and rich worlds for when sleep is not happening."),
        BucketCopy("Late Show", "After the news", "Quiet dramas, sharp thrillers, and series you save for yourself."),
        BucketCopy("Night Owl", "While the world's asleep", "Series with the patience to match your late-night hours."),
    ),
)

private fun dayBucket(hour: Int): String {
    val h = if (hour < 5) hour + 24 else hour
    return when {
        h in 5..11 -> "morning"
        h in 12..16 -> "afternoon"
        h in 17..21 -> "evening"
        else -> "night"
    }
}

private fun dayOfYear(): Int = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)

fun showsEditorial(hour: Int): ShowsEditorial {
    val bucket = dayBucket(hour)
    val variants = BUCKET_VARIANTS.getValue(bucket)
    val idx = (dayOfYear() + (BUCKET_INDEX.getValue(bucket) * 3)) % variants.size
    val copy = variants[idx]
    return ShowsEditorial(
        icon = BUCKET_ICONS.getValue(bucket),
        label = copy.kicker,
        tagline = copy.title,
        description = copy.subtitle,
    )
}
