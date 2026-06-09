package griffio.krogue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import griffio.krogue.game.Direction
import griffio.krogue.game.EffectsState
import griffio.krogue.game.GameState
import griffio.krogue.game.GameStatus
import griffio.krogue.game.Monster
import griffio.krogue.game.Spell

/**
 * The full play screen: a map section on the left and a stacked column of
 * status sections on the right, framed like a tiled terminal. Keyboard input is
 * captured on a focusable root — arrow / WASD / vi (hjkl) keys move, `F` / `B`
 * cast spells at the locked target, `Tab` cycles targets, and `R` restarts.
 */
@Composable
fun GameScreen(game: GameState, effects: EffectsState, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    var target: Monster? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // The locked target, but only while it's alive and still in sight.
    fun activeTarget(): Monster? = target?.takeIf { it in game.visibleMonsters }

    fun fire(spell: Spell) {
        val foe = activeTarget() ?: game.nearestVisibleMonster()
        if (foe == null) {
            game.announce("No target in sight.")
            return
        }
        target = foe
        game.cast(spell, foe)
        // Re-acquire if the target was slain or slipped out of view.
        if (activeTarget() == null) target = game.nearestVisibleMonster()
    }

    fun handle(event: KeyEvent): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        when (event.key) {
            Key.DirectionUp, Key.W, Key.K -> game.move(Direction.NORTH)
            Key.DirectionDown, Key.S, Key.J -> game.move(Direction.SOUTH)
            Key.DirectionLeft, Key.A, Key.H -> game.move(Direction.WEST)
            Key.DirectionRight, Key.D, Key.L -> game.move(Direction.EAST)
            Key.Tab -> target = game.cycleTarget(activeTarget())
            Key.F -> fire(Spell.FIRE_BOLT)
            Key.B -> fire(Spell.ENERGY_BLAST)
            Key.R -> {
                game.startNewGame()
                target = null
            }
            else -> return false
        }
        return true
    }

    Box(
        modifier
            .fillMaxSize()
            .background(TerminalTheme.Background)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { handle(it) },
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Header()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val aimed = activeTarget()
                MapPanel(game, effects, Modifier.weight(1f).fillMaxSize(), aimed?.x, aimed?.y)
                Column(
                    Modifier.width(260.dp).fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    StatsPanel(game, Modifier.fillMaxWidth())
                    LegendPanel(Modifier.fillMaxWidth())
                    MessageLog(game, Modifier.fillMaxWidth())
                    ControlsPanel(Modifier.fillMaxWidth())
                }
            }
        }

        if (game.status != GameStatus.PLAYING) {
            GameOverOverlay(game)
        }
    }
}

@Composable
private fun Header() {
    Column {
        Text(
            "k r o g u e · c o m p o s e",
            color = TerminalTheme.Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "a terminal roguelike rendered in Compose Multiplatform",
            color = TerminalTheme.Dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ControlsPanel(modifier: Modifier = Modifier) {
    TerminalPanel(title = "CONTROLS", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            ControlRow("move", "↑↓←→ / wasd / hjkl")
            ControlRow("attack", "move into a foe")
            ControlRow("target", "Tab to cycle")
            ControlRow("fire bolt", "F")
            ControlRow("energy blast", "B")
            ControlRow("descend / win", "> stairs · * relic")
            ControlRow("new map", "R")
        }
    }
}

@Composable
private fun ControlRow(action: String, keys: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(action, color = TerminalTheme.Dim, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
        Spacer(Modifier.width(8.dp))
        Text(keys, color = TerminalTheme.Foreground, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
    }
}

@Composable
private fun GameOverOverlay(game: GameState) {
    Box(
        Modifier.fillMaxSize().background(TerminalTheme.Background.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        val won = game.status == GameStatus.WON
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (won) "★  YOU WIN  ★" else "✝  YOU DIED  ✝",
                color = if (won) TerminalTheme.Accent else TerminalTheme.Danger,
                fontFamily = FontFamily.Monospace,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "depth ${game.depth} · ${game.gold} gold · ${game.kills} slain · ${game.turn} turns",
                color = TerminalTheme.Foreground,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
            )
            Text(
                "press R to descend anew",
                color = TerminalTheme.Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            )
        }
    }
}
