package griffio.krogue

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import griffio.krogue.game.EffectsState
import griffio.krogue.game.GameState
import griffio.krogue.ui.GameScreen
import griffio.krogue.ui.TerminalTheme

@Composable
fun App() {
    val effects = remember { EffectsState() }
    val game = remember { GameState().apply { onEvent = effects::emit } }
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = TerminalTheme.Background,
            surface = TerminalTheme.PanelBackground,
            primary = TerminalTheme.Accent,
            onBackground = TerminalTheme.Foreground,
            onSurface = TerminalTheme.Foreground,
        ),
    ) {
        GameScreen(game, effects)
    }
}
