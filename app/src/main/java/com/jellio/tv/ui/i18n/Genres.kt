package com.jellio.tv.ui.i18n

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

fun genreDisplayName(genre: String?): String = Genres.translate(genre)
