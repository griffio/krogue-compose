package griffio.krogue.ui

import androidx.compose.ui.graphics.Color
import griffio.krogue.game.Terrain

/**
 * The CRT-amber-meets-ANSI palette shared by every panel. Kept in one place so
 * the whole app reads like a single terminal.
 */
object TerminalTheme {
    val Background = Color(0xFF0B0E0C)
    val PanelBackground = Color(0xFF101412)
    val Border = Color(0xFF2C3A33)
    val Foreground = Color(0xFFBFD8C4)
    val Dim = Color(0xFF53635A)
    val Accent = Color(0xFF7FE3A1)
    val Warn = Color(0xFFE0B24F)
    val Danger = Color(0xFFE0604F)

    val Hero = Color(0xFFFFF44F)

    // Glyph colour for a tile in full view.
    fun colorFor(terrain: Terrain): Color = when (terrain) {
        Terrain.WALL -> Color(0xFF8C9B7A)
        Terrain.FLOOR -> Color(0xFF6E7E72)
        Terrain.DOOR -> Color(0xFFC8862E)
        Terrain.WATER -> Color(0xFF4FA4E0)
        Terrain.TRAP -> Danger
        Terrain.TREASURE -> Warn
        Terrain.STAIRS -> Color(0xFFEAF2EC)
        Terrain.EMPTY -> Background
    }

    // The same tile remembered through fog of war: drawn, but dimmed.
    val FogMemory = Color(0xFF34423A)
}
