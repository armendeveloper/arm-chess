package chess.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Board colour tokens (design §6.2). They are fixed: the same in the light and dark app themes,
 * because every cue and coordinate is checked against the squares themselves (design §5.6).
 */
@Immutable
data class BoardColors(
    val squareLight: Color,
    val squareDark: Color,
    val squareLightLastMove: Color,
    val squareDarkLastMove: Color,
    val coordOnLight: Color,
    val coordOnDark: Color,
    /** Selected border, legal-move dot, capture ring, drop target. */
    val cueInk: Color,
    /** 1.5 dp halo around every ink and check cue. */
    val cueHalo: Color,
    val cueCheck: Color,
    val cueCheckGlyph: Color,
    /** Last-move corner and keyboard focus ring. */
    val cueMark: Color,
)

val ArmChessBoardColors = BoardColors(
    squareLight = Color(0xFFF0D9B5),
    squareDark = Color(0xFF8C6A4E),
    squareLightLastMove = Color(0xFFE8D37A),
    squareDarkLastMove = Color(0xFF857030),
    coordOnLight = Color(0xFF6B4E37),
    coordOnDark = Color(0xFFFFFFFF),
    cueInk = Color(0xFF0D47A1),
    cueHalo = Color(0xFFFFFFFF),
    cueCheck = Color(0xFFB71C1C),
    cueCheckGlyph = Color(0xFFFFFFFF),
    cueMark = Color(0xFF212121),
)

internal val LocalBoardColors = staticCompositionLocalOf { ArmChessBoardColors }
