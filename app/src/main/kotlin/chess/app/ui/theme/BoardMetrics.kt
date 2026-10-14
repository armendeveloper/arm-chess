package chess.app.ui.theme

import androidx.compose.runtime.Immutable

/**
 * Board geometry tokens (design §6.2) resolved to pixels for one square size. Each is a fraction of
 * the square `s`, some with a dp minimum so cues stay visible on small boards. Coordinates are a
 * plain fraction of `s`: they scale with the board, not with the font setting (design §5.9).
 */
@Immutable
data class BoardMetrics(
    val square: Float,
    /** max(12 dp, 0.30 s) */
    val dotDiameter: Float,
    /** 0.92 s */
    val ringDiameter: Float,
    /** max(3 dp, 0.08 s) */
    val ringStroke: Float,
    /** max(3 dp, 0.08 s) */
    val selectedStroke: Float,
    /** 1.5 dp */
    val halo: Float,
    /** max(14 dp, 0.36 s) */
    val checkBadge: Float,
    /** 0.05 s: the "+" on the check badge. */
    val checkGlyphStroke: Float,
    /** max(8 dp, 0.22 s): the legs of the last-move corner triangle. */
    val lastMoveCorner: Float,
    /** 0.24 s */
    val coordSize: Float,
    /** 2 dp: coordinates and the check badge sit this far in from the square's edge. */
    val coordInset: Float,
) {
    companion object {
        /** [squarePx] is the square's side in pixels; [density] is pixels per dp. */
        fun of(squarePx: Float, density: Float): BoardMetrics {
            fun dp(value: Float) = value * density
            val s = squarePx
            return BoardMetrics(
                square = s,
                dotDiameter = maxOf(dp(12f), 0.30f * s),
                ringDiameter = 0.92f * s,
                ringStroke = maxOf(dp(3f), 0.08f * s),
                selectedStroke = maxOf(dp(3f), 0.08f * s),
                halo = dp(1.5f),
                checkBadge = maxOf(dp(14f), 0.36f * s),
                checkGlyphStroke = 0.05f * s,
                lastMoveCorner = maxOf(dp(8f), 0.22f * s),
                coordSize = 0.24f * s,
                coordInset = dp(2f),
            )
        }
    }
}
