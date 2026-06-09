package griffio.krogue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import griffio.krogue.game.EffectsState

/**
 * Maps a world cell to a pixel offset inside the map panel. Built by [MapPanel]
 * from the same camera origin and the measured monospace cell size, so the
 * overlay lines up exactly with the glyph grid beneath it.
 */
data class GridMetrics(
    val cellWidth: Float,
    val cellHeight: Float,
    val originX: Int,
    val originY: Int,
)

/**
 * The real-time render layer: a transparent Canvas over the text grid that draws
 * [EffectsState]'s floating particles, driven by a `withFrameNanos` loop. The
 * loop only spins while particles exist, so an idle game costs nothing. This is
 * the seam future particle effects (sparks, explosions) plug into — they become
 * new particle shapes drawn here, fed by new `GameEvent`s.
 */
@Composable
fun EffectsOverlay(effects: EffectsState, metrics: GridMetrics, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()

    LaunchedEffect(effects.particles.isNotEmpty()) {
        while (effects.particles.isNotEmpty()) {
            withFrameNanos { effects.advance(it) }
        }
    }

    Canvas(modifier) {
        effects.tick // observe: redraw this Canvas on every advanced frame
        effects.particles.forEach { p ->
            val frac = (p.ageNanos.toFloat() / p.lifeNanos).coerceIn(0f, 1f)
            val alpha = 1f - frac
            val px = (p.worldX - metrics.originX) * metrics.cellWidth
            val py = (p.worldY - metrics.originY - frac * EffectsState.RISE_CELLS) * metrics.cellHeight
            if (px < -metrics.cellWidth || py < -metrics.cellHeight) return@forEach
            if (px > size.width || py > size.height) return@forEach
            drawText(
                textMeasurer = measurer,
                text = p.text,
                topLeft = Offset(px, py),
                style = TextStyle(
                    color = p.color.copy(alpha = alpha),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }
}
