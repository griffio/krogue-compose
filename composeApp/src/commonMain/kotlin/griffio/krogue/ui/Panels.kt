package griffio.krogue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import griffio.krogue.game.GameState
import griffio.krogue.game.GameStatus

/** A titled, bordered box — the recurring frame around every section. */
@Composable
fun TerminalPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .background(TerminalTheme.PanelBackground, RoundedCornerShape(6.dp))
            .border(1.dp, TerminalTheme.Border, RoundedCornerShape(6.dp))
            .padding(6.dp),
    ) {
        Text(
            text = "┤ $title ├",
            color = TerminalTheme.Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun StatLine(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = TerminalTheme.Foreground) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        MonoLabel(label, TerminalTheme.Dim)
        MonoLabel(value, valueColor)
    }
}

@Composable
private fun MonoLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
}

@Composable
fun StatsPanel(game: GameState, modifier: Modifier = Modifier) {
    TerminalPanel(title = "HERO", modifier = modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val hpColor = when {
                game.hp <= game.maxHp / 4 -> TerminalTheme.Danger
                game.hp <= game.maxHp / 2 -> TerminalTheme.Warn
                else -> TerminalTheme.Accent
            }
            StatLine("HP", "${game.hp}/${game.maxHp}", hpColor)
            MonoLabel(healthBar(game.hp, game.maxHp), hpColor)
            StatLine("MP", "${game.mp}/${game.maxMp}", TerminalTheme.Mana)
            MonoLabel(healthBar(game.mp, game.maxMp), TerminalTheme.Mana)
            Spacer(Modifier.height(2.dp))
            StatLine("Gold", "${game.gold}", TerminalTheme.Warn)
            StatLine("Slain", "${game.kills}", TerminalTheme.Foreground)
            StatLine("Foes", "${game.monsters.size}", TerminalTheme.Danger)
            StatLine("Depth", "${game.depth}/${griffio.krogue.game.GameState.MAX_DEPTH}", TerminalTheme.Foreground)
            StatLine("Turn", "${game.turn}", TerminalTheme.Dim)
            StatLine("Pos", "${game.heroX},${game.heroY}", TerminalTheme.Dim)
            Spacer(Modifier.height(4.dp))
            val statusText = when (game.status) {
                GameStatus.PLAYING -> "▸ exploring"
                GameStatus.WON -> "★ victorious"
                GameStatus.DEAD -> "✝ deceased"
            }
            MonoLabel(statusText, TerminalTheme.Accent)
        }
    }
}

private fun healthBar(hp: Int, maxHp: Int, width: Int = 14): String {
    val filled = if (maxHp == 0) 0 else (hp * width / maxHp).coerceIn(0, width)
    return "[" + "█".repeat(filled) + "·".repeat(width - filled) + "]"
}

@Composable
fun MessageLog(game: GameState, modifier: Modifier = Modifier) {
    TerminalPanel(title = "LOG", modifier = modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            val recent = game.messages.takeLast(7)
            if (recent.isEmpty()) {
                MonoLabel("…", TerminalTheme.Dim)
            }
            recent.forEachIndexed { index, message ->
                // Fade older lines toward the background.
                val faded = index < recent.lastIndex - 2
                MonoLabel(message, if (faded) TerminalTheme.Dim else TerminalTheme.Foreground)
            }
        }
    }
}

@Composable
fun LegendPanel(modifier: Modifier = Modifier) {
    TerminalPanel(title = "LEGEND", modifier = modifier) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            LegendRow("@", "you", TerminalTheme.Hero)
            LegendRow("#", "wall", TerminalTheme.colorFor(griffio.krogue.game.Terrain.WALL))
            LegendRow(".", "floor", TerminalTheme.colorFor(griffio.krogue.game.Terrain.FLOOR))
            LegendRow("+", "door", TerminalTheme.colorFor(griffio.krogue.game.Terrain.DOOR))
            LegendRow("~", "water", TerminalTheme.colorFor(griffio.krogue.game.Terrain.WATER))
            LegendRow("^", "trap", TerminalTheme.colorFor(griffio.krogue.game.Terrain.TRAP))
            LegendRow("$", "gold", TerminalTheme.colorFor(griffio.krogue.game.Terrain.TREASURE))
            LegendRow(">", "stairs", TerminalTheme.colorFor(griffio.krogue.game.Terrain.STAIRS))
            LegendRow("*", "relic", TerminalTheme.colorFor(griffio.krogue.game.Terrain.AMULET))
            LegendRow("rkso", "monsters", TerminalTheme.colorFor(griffio.krogue.game.MonsterKind.KOBOLD))
            LegendRow("w", "wisp (ranged)", TerminalTheme.colorFor(griffio.krogue.game.MonsterKind.WISP))
        }
    }
}

@Composable
private fun LegendRow(glyph: String, label: String, color: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(glyph, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(2.dp))
        MonoLabel(label, TerminalTheme.Dim)
    }
}
