package com.jellio.tv.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

val MovieIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "JellioMovie",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Bottom body
        addPath(
            pathData = PathParser().parsePathString("M 4.6,11 h 14.8 a 1.6,1.6 0 0,1 1.6,1.6 v 6.8 a 1.6,1.6 0 0,1 -1.6,1.6 h -14.8 a 1.6,1.6 0 0,1 -1.6,-1.6 v -6.8 a 1.6,1.6 0 0,1 1.6,-1.6 Z").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        // Horizontal line inside body
        addPath(
            pathData = PathParser().parsePathString("M7 16L17 16").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeAlpha = 0.4f,
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        // Top clapper
        addPath(
            pathData = PathParser().parsePathString("M 3.9,6 h 16.2 a 0.9,0.9 0 0,1 0.9,0.9 v 3.2 a 0.9,0.9 0 0,1 -0.9,0.9 h -16.2 a 0.9,0.9 0 0,1 -0.9,-0.9 v -3.2 a 0.9,0.9 0 0,1 0.9,-0.9 Z").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = SolidColor(Color.Black),
            fillAlpha = 0.14f
        )
        // Clapper stripes
        addPath(
            pathData = PathParser().parsePathString("M5.5 11L8.5 6M11 11L14 6M16.5 11L19.5 6").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

val TvShowsIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "JellioShows",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Antenna
        addPath(
            pathData = PathParser().parsePathString("M8.5 3L11.5 8M15.5 3L12.5 8").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        // TV Body
        addPath(
            pathData = PathParser().parsePathString("M 5,8 h 14 a 2,2 0 0,1 2,2 v 9 a 2,2 0 0,1 -2,2 h -14 a 2,2 0 0,1 -2,-2 v -9 a 2,2 0 0,1 2,-2 Z").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

val AnimeIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "JellioAnime",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Fox head
        addPath(
            pathData = PathParser().parsePathString("M12 5c.67 0 1.35.09 2 .26 1.78-2 5.03-2.84 6.42-2.26 1.4.58-.42 7-.42 11 0 5.5-2.5 10-10 10S0 19.5 0 14c0-4 1.82-10.42 3.42-11 1.39-.58 4.64.26 6.42 2.26C10.65 5.09 11.33 5 12 5z").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
        // Eyes
        addPath(
            pathData = PathParser().parsePathString("M8 14v.5M16 14v.5").toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }.build()
}

fun getLibraryIcon(collectionType: String?): ImageVector {
    return when (collectionType?.lowercase()) {
        "movies" -> MovieIconVector
        "tvshows", "shows", "series" -> TvShowsIconVector
        "anime" -> AnimeIconVector
        else -> LibraryIconVector
    }
}
