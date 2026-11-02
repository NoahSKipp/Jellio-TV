package com.jellio.tv.ui.nav

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

val SearchIconVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "JellioSearch",
        defaultWidth = 20.dp,
        defaultHeight = 20.dp,
        viewportWidth = 20f,
        viewportHeight = 20f,
    ).apply {
        addPath(
            pathData = PathParser().parsePathString(
                "M4 9a5 5 0 1110 0A5 5 0 014 9zm5-7a7 7 0 104.2 12.6.999.999 0 00.093.107l3 3a1 1 0 001.414-1.414l-3-3a.999.999 0 00-.107-.093A7 7 0 009 2z",
            ).toNodes(),
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        )
    }.build()
}
