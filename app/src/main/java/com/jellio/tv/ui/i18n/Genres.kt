package com.jellio.tv.ui.i18n

import com.jellio.tv.data.model.BaseItemDto
import com.jellio.tv.data.prefs.DisplayPreferences

object Genres {
    private val translations: Map<String, Map<String, String>> = mapOf(
        "Action" to mapOf("en" to "Action", "de" to "Action", "es" to "Acción", "pl" to "Akcja", "fr" to "Action", "it" to "Azione", "pt" to "Ação"),
        "Adventure" to mapOf("en" to "Adventure", "de" to "Abenteuer", "es" to "Aventura", "pl" to "Przygodowy", "fr" to "Aventure", "it" to "Avventura", "pt" to "Aventura"),
        "Animation" to mapOf("en" to "Animation", "de" to "Animation", "es" to "Animación", "pl" to "Animacja", "fr" to "Animation", "it" to "Animazione", "pt" to "Animação"),
        "Comedy" to mapOf("en" to "Comedy", "de" to "Komödie", "es" to "Comedia", "pl" to "Komedia", "fr" to "Comédie", "it" to "Commedia", "pt" to "Comédia"),
        "Crime" to mapOf("en" to "Crime", "de" to "Krimi", "es" to "Crimen", "pl" to "Kryminał", "fr" to "Crime", "it" to "Crime", "pt" to "Crime"),
        "Documentary" to mapOf("en" to "Documentary", "de" to "Dokumentation", "es" to "Documental", "pl" to "Dokumentalny", "fr" to "Documentaire", "it" to "Documentario", "pt" to "Documentário"),
        "Drama" to mapOf("en" to "Drama", "de" to "Drama", "es" to "Drama", "pl" to "Dramat", "fr" to "Drame", "it" to "Dramma", "pt" to "Drama"),
        "Family" to mapOf("en" to "Family", "de" to "Familie", "es" to "Familia", "pl" to "Familijny", "fr" to "Famille", "it" to "Famiglia", "pt" to "Família"),
        "Fantasy" to mapOf("en" to "Fantasy", "de" to "Fantasy", "es" to "Fantasía", "pl" to "Fantasy", "fr" to "Fantastique", "it" to "Fantasy", "pt" to "Fantasia"),
        "History" to mapOf("en" to "History", "de" to "Historie", "es" to "Historia", "pl" to "Historyczny", "fr" to "Histoire", "it" to "Storia", "pt" to "História"),
        "Horror" to mapOf("en" to "Horror", "de" to "Horror", "es" to "Terror", "pl" to "Horror", "fr" to "Horreur", "it" to "Horror", "pt" to "Terror"),
        "Music" to mapOf("en" to "Music", "de" to "Musik", "es" to "Música", "pl" to "Muzyczny", "fr" to "Musique", "it" to "Musica", "pt" to "Música"),
        "Musical" to mapOf("en" to "Musical", "de" to "Musical", "es" to "Musical", "pl" to "Musical", "fr" to "Comédie musicale", "it" to "Musical", "pt" to "Musical"),
        "Mystery" to mapOf("en" to "Mystery", "de" to "Mystery", "es" to "Misterio", "pl" to "Tajemnica", "fr" to "Mystère", "it" to "Mistero", "pt" to "Mistério"),
        "Romance" to mapOf("en" to "Romance", "de" to "Liebesfilm", "es" to "Romance", "pl" to "Romans", "fr" to "Romance", "it" to "Romantico", "pt" to "Romance"),
        "Science Fiction" to mapOf("en" to "Science Fiction", "de" to "Science-Fiction", "es" to "Ciencia ficción", "pl" to "Science Fiction", "fr" to "Science-Fiction", "it" to "Fantascienza", "pt" to "Ficção científica"),
        "Sci-Fi" to mapOf("en" to "Sci-Fi", "de" to "Science-Fiction", "es" to "Ciencia ficción", "pl" to "Sci-Fi", "fr" to "Science-Fiction", "it" to "Fantascienza", "pt" to "Ficção científica"),
        "TV Movie" to mapOf("en" to "TV Movie", "de" to "Fernsehfilm", "es" to "Película de TV", "pl" to "Film telewizyjny", "fr" to "Téléfilm", "it" to "Film TV", "pt" to "Telefilme"),
        "Thriller" to mapOf("en" to "Thriller", "de" to "Thriller", "es" to "Thriller", "pl" to "Thriller", "fr" to "Thriller", "it" to "Thriller", "pt" to "Thriller"),
        "War" to mapOf("en" to "War", "de" to "Kriegsfilm", "es" to "Bélico", "pl" to "Wojenny", "fr" to "Guerre", "it" to "Guerra", "pt" to "Guerra"),
        "Western" to mapOf("en" to "Western", "de" to "Western", "es" to "Western", "pl" to "Western", "fr" to "Western", "it" to "Western", "pt" to "Western"),
        "Action & Adventure" to mapOf("en" to "Action & Adventure", "de" to "Action & Abenteuer", "es" to "Acción y aventura", "pl" to "Akcja i przygoda", "fr" to "Action & Aventure", "it" to "Azione e avventura", "pt" to "Ação e aventura"),
        "Sci-Fi & Fantasy" to mapOf("en" to "Sci-Fi & Fantasy", "de" to "Sci-Fi & Fantasy", "es" to "Ciencia ficción y fantasía", "pl" to "Sci-Fi i Fantasy", "fr" to "Science-Fiction & Fantastique", "it" to "Fantascienza e Fantasy", "pt" to "Ficção científica e fantasia"),
        "Kids" to mapOf("en" to "Kids", "de" to "Kinder", "es" to "Infantil", "pl" to "Dla dzieci", "fr" to "Enfants", "it" to "Bambini", "pt" to "Crianças"),
        "News" to mapOf("en" to "News", "de" to "Nachrichten", "es" to "Noticias", "pl" to "Wiadomości", "fr" to "Actualités", "it" to "Notizie", "pt" to "Notícias"),
        "Reality" to mapOf("en" to "Reality", "de" to "Reality-TV", "es" to "Reality", "pl" to "Reality Show", "fr" to "Téléréalité", "it" to "Reality", "pt" to "Reality"),
        "Soap" to mapOf("en" to "Soap", "de" to "Telenovela", "es" to "Telenovela", "pl" to "Telenowela", "fr" to "Feuilleton", "it" to "Soap opera", "pt" to "Telenovela"),
        "Talk" to mapOf("en" to "Talk", "de" to "Talkshow", "es" to "Talk Show", "pl" to "Talk-show", "fr" to "Talk-show", "it" to "Talk Show", "pt" to "Talk Show"),
        "War & Politics" to mapOf("en" to "War & Politics", "de" to "Krieg & Politik", "es" to "Bélico y política", "pl" to "Wojna i polityka", "fr" to "Guerre & Politique", "it" to "Guerra e politica", "pt" to "Guerra e política"),
        "Anime" to mapOf("en" to "Anime", "de" to "Anime", "es" to "Anime", "pl" to "Anime", "fr" to "Anime", "it" to "Anime", "pt" to "Anime"),
        "Biography" to mapOf("en" to "Biography", "de" to "Biografie", "es" to "Biografía", "pl" to "Biografia", "fr" to "Biographie", "it" to "Biografia", "pt" to "Biografia"),
        "Sport" to mapOf("en" to "Sport", "de" to "Sport", "es" to "Deportes", "pl" to "Sport", "fr" to "Sport", "it" to "Sport", "pt" to "Desporto"),
        "Short" to mapOf("en" to "Short", "de" to "Kurzfilm", "es" to "Cortometraje", "pl" to "Krótkometrażowy", "fr" to "Court-métrage", "it" to "Cortometraggio", "pt" to "Curta-metragem"),
        "Film-Noir" to mapOf("en" to "Film-Noir", "de" to "Film Noir", "es" to "Cine negro", "pl" to "Film-Noir", "fr" to "Film noir", "it" to "Film noir", "pt" to "Filme noir"),
        "Game-Show" to mapOf("en" to "Game-Show", "de" to "Spielshow", "es" to "Concurso", "pl" to "Teleturniej", "fr" to "Jeu télévisé", "it" to "Quiz", "pt" to "Concurso"),
    )

    // Fast case-insensitive fallback lookup map
    private val lowerMap: Map<String, Map<String, String>> = translations.mapKeys { it.key.lowercase().trim() }

    fun translate(genre: String?, languageCode: String? = null): String {
        if (genre.isNullOrBlank()) return ""
        val lang = (languageCode ?: DisplayPreferences.displayLanguage.code).lowercase().trim()
        val direct = translations[genre]?.get(lang)
        if (direct != null) return direct
        val lower = genre.lowercase().trim()
        val matched = lowerMap[lower]?.get(lang)
        if (matched != null) return matched
        return genre
    }
}

fun displayLocale(): java.util.Locale = DisplayPreferences.displayLanguage.locale

fun libraryDisplayName(name: String?, collectionType: String? = null): String {
    val trimmed = name?.trim()
    if (trimmed.isNullOrBlank()) {
        return when (collectionType?.lowercase()) {
            "movies" -> Strings.get("nav_movies")
            "tvshows" -> Strings.get("nav_shows")
            "books" -> Strings.get("nav_books")
            "music" -> Strings.get("nav_music")
            "boxsets" -> Strings.get("detail_collection")
            else -> Strings.get("library_default_title")
        }
    }
    val lower = trimmed.lowercase()
    if (lower == "anime") return Strings.get("library_anime_title")
    if (lower in setOf("movies", "movie", "films", "film", "filme", "películas", "peliculas", "filmy", "filmes")) return Strings.get("nav_movies")
    if (lower in setOf("shows", "tv shows", "tv", "series", "tv series", "serien", "séries", "seriale", "serie tv", "programas")) return Strings.get("nav_shows")
    if (lower in setOf("books", "book", "bücher", "libros", "livres", "libri", "książki", "livros")) return Strings.get("nav_books")
    if (lower == "manga") return Strings.get("nav_manga")
    if (lower in setOf("audiobooks", "audiobook", "hörbücher", "audiolibros", "livres audio", "audiolibri", "audiobooki", "audiolivros")) return Strings.get("nav_audiobooks")
    if (lower in setOf("music", "musik", "música", "musique", "musica", "muzyka")) return Strings.get("nav_music")
    if (lower in setOf("collections", "collection", "boxsets", "box sets")) return Strings.get("detail_collection")
    if (lower == "library") return Strings.get("library_default_title")
    return name
}

fun catalogDisplayName(name: String?, kind: String? = null, service: String? = null): String {
    val raw = name?.trim().orEmpty()
    if (service != null && (raw.isEmpty() || raw.equals(service, ignoreCase = true))) {
        return if (kind == "tvshows") Strings.format("service_shows_on", service) else Strings.format("service_movies_on", service)
    }
    val lower = raw.lowercase()
    return when {
        lower == "trending movies" || (lower == "trending" && kind == "movies") ->
            Strings.get("catalog_trending_movies")
        lower in setOf("trending shows", "trending tv shows", "trending series") || (lower == "trending" && kind == "tvshows") ->
            Strings.get("catalog_trending_shows")
        lower == "popular movies" || (lower == "popular" && kind == "movies") ->
            Strings.get("catalog_popular_movies")
        lower in setOf("popular shows", "popular tv shows", "popular series") || (lower == "popular" && kind == "tvshows") ->
            Strings.get("catalog_popular_shows")
        lower in setOf("top rated movies", "top-rated movies") || (lower in setOf("top rated", "top-rated") && kind == "movies") ->
            Strings.get("catalog_top_rated_movies")
        lower in setOf("top rated shows", "top-rated shows", "top rated tv shows", "top rated series") || (lower in setOf("top rated", "top-rated") && kind == "tvshows") ->
            Strings.get("catalog_top_rated_shows")
        lower in setOf("new releases movies", "new movies") || (lower == "new releases" && kind == "movies") ->
            Strings.get("catalog_new_movies")
        lower in setOf("new releases shows", "new shows", "new series") || (lower == "new releases" && kind == "tvshows") ->
            Strings.get("catalog_new_shows")
        lower == "trending" ->
            Strings.get("catalog_trending")
        lower == "popular" ->
            Strings.get("catalog_popular")
        lower in setOf("top rated", "top-rated") ->
            Strings.get("catalog_top_rated")
        lower == "new releases" ->
            Strings.get("catalog_new_releases")
        lower.startsWith("shows on ") ->
            Strings.format("service_shows_on", raw.substring("shows on ".length).trim())
        lower.startsWith("movies on ") ->
            Strings.format("service_movies_on", raw.substring("movies on ".length).trim())
        kind == "tvshows" && !lower.contains("show") && !lower.contains("serie") ->
            Strings.format("catalog_named_shows", raw)
        kind == "movies" && !lower.contains("movie") && !lower.contains("film") ->
            Strings.format("catalog_named_movies", raw)
        else -> raw
    }
}

private val badgeNameKeyByEnglishName: Map<String, String> by lazy {
    StringsEn.MAP.filterKeys { it.startsWith("badge_name_") }
        .entries.associate { it.value.trim().lowercase() to it.key }
}

fun badgeDisplayName(id: String?, fallback: String? = null): String {
    if (!id.isNullOrBlank()) {
        val key = "badge_name_" + id.trim().lowercase().replace('-', '_')
        val table = Strings.tableFor(DisplayPreferences.displayLanguage)
        table[key]?.let { return it }
        StringsEn.MAP[key]?.let { return it }
    }
    if (!fallback.isNullOrBlank()) {
        val key = badgeNameKeyByEnglishName[fallback.trim().lowercase()]
        if (key != null) return Strings.get(key)
        return fallback
    }
    return id ?: ""
}

private val badgeDescKeyByEnglishDesc: Map<String, String> by lazy {
    StringsEn.MAP.filterKeys { it.startsWith("badge_desc_") }
        .entries.associate { it.value.trim().lowercase() to it.key }
}

fun badgeDisplayDescription(id: String?, fallback: String? = null): String {
    if (!id.isNullOrBlank()) {
        val key = "badge_desc_" + id.trim().lowercase().replace('-', '_')
        val table = Strings.tableFor(DisplayPreferences.displayLanguage)
        table[key]?.let { return it }
        StringsEn.MAP[key]?.let { return it }
    }
    if (!fallback.isNullOrBlank()) {
        val key = badgeDescKeyByEnglishDesc[fallback.trim().lowercase()]
        if (key != null) return Strings.get(key)
        return fallback
    }
    return ""
}

fun formatDurationMinutes(minutes: Int): String {
    if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        return if (m > 0) Strings.format("duration_hours_minutes", h, m) else Strings.format("duration_hours", h)
    } else {
        return Strings.format("duration_minutes", minutes)
    }
}

fun seasonEpisodeTag(season: Int?, episode: Int?): String {
    return when {
        season != null && episode != null -> Strings.format("tag_season_episode", season, episode)
        episode != null -> Strings.format("tag_episode", episode)
        season != null -> seasonDisplayName(season, null)
        else -> ""
    }
}

fun genreDisplayName(genre: String?): String = Genres.translate(genre)

fun seasonDisplayName(seasonNumber: Int?, name: String? = null): String {
    if (seasonNumber != null && seasonNumber > 0) {
        return Strings.format("detail_season_number", seasonNumber)
    }
    val raw = name.orEmpty().trim()
    val match = Regex("""(?i)^Season\s+(\d+)$""").matchEntire(raw)
    if (match != null) {
        val num = match.groupValues[1].toIntOrNull()
        if (num != null) {
            return Strings.format("detail_season_number", num)
        }
    }
    if (seasonNumber == 0 || raw.contains("special", ignoreCase = true)) {
        return Strings.get("detail_specials").ifEmpty { "Specials" }
    }
    return raw
}

fun seasonDisplayName(season: BaseItemDto): String = seasonDisplayName(season.IndexNumber, season.Name)

fun episodeDisplayName(name: String?, indexNumber: Int? = null): String {
    val raw = name.orEmpty().trim()
    val match = Regex("""(?i)^Episode\s+(\d+)$""").matchEntire(raw)
    if (match != null) {
        val num = match.groupValues[1].toIntOrNull()
        if (num != null) {
            return Strings.format("detail_episode_number", num)
        }
    }
    if (raw.isBlank() && indexNumber != null) {
        return Strings.format("detail_episode_number", indexNumber)
    }
    return raw
}
