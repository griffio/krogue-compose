package griffio.krogue.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import griffio.krogue.game.Bolt
import griffio.krogue.game.Burst
import griffio.krogue.game.EffectsState
import griffio.krogue.game.FloatingText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
 * [EffectsState]'s particles — floating numbers, spell bolts with trails, and
 * impact bursts — driven by a `withFrameNanos` loop. The loop only spins while
 * particles exist, so an idle game costs nothing.
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

        fun glyph(text: String, worldX: Float, worldY: Float, color: Color, sizeSp: Float, bold: Boolean) {
            val px = (worldX - metrics.originX) * metrics.cellWidth
            val py = (worldY - metrics.originY) * metrics.cellHeight
            if (px < -metrics.cellWidth || py < -metrics.cellHeight) return
            if (px > size.width || py > size.height) return
            drawText(
                textMeasurer = measurer,
                text = text,
                topLeft = Offset(px, py),
                style = TextStyle(
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    fontSize = sizeSp.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                ),
            )
        }

        effects.particles.forEach { p ->
            when (p) {
                is FloatingText -> {
                    val f = p.fraction
                    glyph(p.text, p.worldX, p.worldY - f * EffectsState.RISE_CELLS, p.color.copy(alpha = 1f - f), 13f, true)
                }

                is Bolt -> {
                    // A hot head glyph plus a few cooling, fading trail glyphs
                    // sampled back along the segment.
                    val f = p.fraction
                    val trail = 4
                    for (j in 0..trail) {
                        val tf = f - j * 0.06f
                        if (tf < 0f) break
                        val wx = p.fromX + (p.toX - p.fromX) * tf
                        val wy = p.fromY + (p.toY - p.fromY) * tf
                        val color = EffectsState.spellColor(p.kind, j.toFloat() / trail)
                        val alpha = 1f - j.toFloat() / (trail + 1)
                        glyph(if (j == 0) "*" else "∙", wx, wy, color.copy(alpha = alpha), 15f, j == 0)
                    }
                }

                is Burst -> {
                    val f = p.fraction
                    val radius = f * p.maxRadius
                    val color = EffectsState.spellColor(p.kind, f).copy(alpha = 1f - f)
                    if (f < 0.35f) glyph("*", p.centerX, p.centerY, color, 16f, true)
                    val n = 12
                    for (k in 0 until n) {
                        val ang = (k.toFloat() / n) * 2f * PI.toFloat()
                        glyph("*", p.centerX + cos(ang) * radius, p.centerY + sin(ang) * radius, color, 14f, false)
                    }
                }
            }
        }
    }
}
