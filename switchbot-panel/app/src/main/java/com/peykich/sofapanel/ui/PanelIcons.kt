package com.peykich.sofapanel.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 線画アイコン（線幅 1.5、24×24 基準）。絵文字は使わない */
object PanelIcons {
    private fun line(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Power = line("power", "M12 3v8", "M6.6 6.6a8 8 0 1 0 10.8 0")
    val Play = line("play", "M8 5l11 7-11 7z")
    val Pause = line("pause", "M8 5v14", "M16 5v14")
    val Film = line("film", "M4 5h16v14H4z", "M8 5v14", "M16 5v14", "M4 9.5h4", "M4 14.5h4", "M16 9.5h4", "M16 14.5h4")
    val Moon = line("moon", "M19.5 14.5A8 8 0 1 1 9.5 4.5a6.5 6.5 0 0 0 10 10z")
    val Sun = line(
        "sun", "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2.5v2", "M12 19.5v2", "M2.5 12h2", "M19.5 12h2",
        "M5.3 5.3l1.4 1.4", "M17.3 17.3l1.4 1.4", "M5.3 18.7l1.4-1.4", "M17.3 6.7l1.4-1.4",
    )
    val Bulb = line("bulb", "M9.5 18h5", "M10.5 21h3", "M12 3a6 6 0 0 0-3.5 10.9V16h7v-2.1A6 6 0 0 0 12 3z")
    val Tv = line("tv", "M3 6h18v12H3z", "M8 21h8")
    val Projector = line("projector", "M3 8h18v8H3z", "M13 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0", "M6 11h4", "M6 16v2", "M18 16v2")
    val Screen = line("screen", "M3 4h18", "M5 4v11h14V4", "M12 15v3")
    val Sofa = line("sofa", "M6 11V8a3 3 0 0 1 3-3h6a3 3 0 0 1 3 3v3", "M4 11a2 2 0 0 1 2 2v2h12v-2a2 2 0 0 1 4 0v5H2v-5a2 2 0 0 1 2-2z", "M5 18v2", "M19 18v2")
    val Home = line("home", "M4 11l8-7 8 7", "M6 9.5V20h12V9.5", "M10 20v-5h4v5")
    val Up = line("up", "M12 19V5", "M6 11l6-6 6 6")
    val Down = line("down", "M12 5v14", "M6 13l6 6 6-6")
    val Stop = line("stop", "M7 7h10v10H7z")
    val Plus = line("plus", "M12 5v14", "M5 12h14")
    val Minus = line("minus", "M5 12h14")
    val VolumeUp = line("volup", "M4 9h4l5-4v14l-5-4H4z", "M16.5 9a4 4 0 0 1 0 6", "M19 6.5a8 8 0 0 1 0 11")
    val VolumeDown = line("voldown", "M4 9h4l5-4v14l-5-4H4z", "M16.5 9a4 4 0 0 1 0 6")
    val Clock = line("clock", "M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z", "M12 7v5l3 2")
    val Scene = line("scene", "M12 3l2.2 5.6L20 9.3l-4.4 3.9 1.3 5.8L12 16l-4.9 3 1.3-5.8L4 9.3l5.8-.7z")
    val Steps = line("steps", "M5 6h1", "M10 6h9", "M5 12h1", "M10 12h9", "M5 18h1", "M10 18h9")
    val Handle = line("handle", "M7 8h10", "M7 12h10", "M7 16h10")
    val Send = line("send", "M4 12l16-8-6 16-2.5-6.5z", "M11.5 13.5L20 4")
    val Trash = line("trash", "M5 7h14", "M10 7V4.5h4V7", "M7 7l1 13h8l1-13")
    val Copy = line("copy", "M9 9h11v11H9z", "M5 15V4h11")
    val Close = line("close", "M6 6l12 12", "M18 6L6 18")
    val Check = line("check", "M5 12.5l4.5 4.5L19 7.5")
    val Warning = line("warning", "M12 4l9 16H3z", "M12 10v4", "M12 17v.5")
    val Offline = line("offline", "M3 3l18 18", "M8.5 16.5a5 5 0 0 1 7 0", "M5 12.8a10 10 0 0 1 4-2.5", "M14.8 10.2a10 10 0 0 1 4.2 2.6", "M2 9.3a15 15 0 0 1 4.5-3", "M12 20v.5")
    val Back = line("back", "M15 5l-7 7 7 7")
    val Thermo = line("thermo", "M10 14V5.5a2 2 0 1 1 4 0V14a4 4 0 1 1-4 0z")
    val Drop = line("drop", "M12 3.5s6 6.8 6 10.5a6 6 0 0 1-12 0c0-3.7 6-10.5 6-10.5z")
    val Settings = line("settings", "M4 7h9", "M17 7h3", "M4 17h3", "M11 17h9", "M15 5v4", "M9 15v4")
    val Plug = line("plug", "M9 3v5", "M15 3v5", "M6 8h12v3a6 6 0 0 1-12 0z", "M12 17v4")
    val Battery = line("battery", "M3 8h16v8H3z", "M21 11v2")

    /** マクロ・カードに選べるアイコン */
    val choices: List<Pair<String, ImageVector>> = listOf(
        "play" to Play, "film" to Film, "moon" to Moon, "sun" to Sun, "bulb" to Bulb,
        "tv" to Tv, "projector" to Projector, "screen" to Screen, "sofa" to Sofa, "home" to Home,
    )

    fun byName(name: String): ImageVector = choices.firstOrNull { it.first == name }?.second ?: Play
}
