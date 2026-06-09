package griffio.krogue.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import griffio.krogue.game.GameState
import kotlin.math.max
import kotlin.math.min

/** Columns/rows of the camera that follows the hero. */
private const val VIEW_COLS = 60
private const val VIEW_ROWS = 32

/**
 * Draws the dungeon as a grid of glyphs, one [AnnotatedString] per row so each
 * line is a single text node with per-character colour spans — cheap to
 * recompose. The camera centres on the hero and clamps to the map edges.
 */
@Composable
fun MapPanel(game: GameState, modifier: Modifier = Modifier) {
    TerminalPanel(title = "DUNGEON · depth ${game.depth}", modifier = modifier) {
        val cols = min(VIEW_COLS, game.width)
        val rows = min(VIEW_ROWS, game.height)
        val originX = (game.heroX - cols / 2).coerceIn(0, max(0, game.width - cols))
        val originY = (game.heroY - rows / 2).coerceIn(0, max(0, game.height - rows))

        Box(Modifier.padding(2.dp)) {
            MonospaceText(buildMap(game, originX, originY, cols, rows))
        }
    }
}

private fun buildMap(
    game: GameState,
    originX: Int,
    originY: Int,
    cols: Int,
    rows: Int,
): AnnotatedString = buildAnnotatedString {
    for (sy in 0 until rows) {
        val y = originY + sy
        for (sx in 0 until cols) {
            val x = originX + sx
            when {
                x == game.heroX && y == game.heroY ->
                    withStyle(SpanStyle(color = TerminalTheme.Hero)) { append('@') }

                game.isVisible(x, y) -> {
                    val terrain = game.dungeon.terrainAt(x, y)
                    withStyle(SpanStyle(color = TerminalTheme.colorFor(terrain))) {
                        append(terrain.glyph)
                    }
                }

                game.isExplored(x, y) -> {
                    val terrain = game.dungeon.terrainAt(x, y)
                    withStyle(SpanStyle(color = TerminalTheme.FogMemory)) {
                        append(terrain.glyph)
                    }
                }

                else -> append(' ')
            }
        }
        if (sy < rows - 1) append('\n')
    }
}

@Composable
fun MonospaceText(text: AnnotatedString) {
    androidx.compose.material3.Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 15.sp,
        lineHeight = 16.sp,
        color = TerminalTheme.Foreground,
    )
}
