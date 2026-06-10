package griffio.krogue

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import griffio.krogue.game.EffectsState
import griffio.krogue.game.GameAudio
import griffio.krogue.game.GameState
import griffio.krogue.game.createAudioEngine
import griffio.krogue.ui.GameScreen
import griffio.krogue.ui.TerminalTheme

@Composable
fun App() {
    val effects = remember { EffectsState() }
    val audio = remember { GameAudio(createAudioEngine()) }
    // World events fan out to both layers: particles to watch, sound to hear.
    val game = remember { GameState().apply { onEvent = { effects.emit(it); audio.emit(it) } } }

    LaunchedEffect(Unit) { audio.startMusic() }
    DisposableEffect(Unit) { onDispose { audio.release() } }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = TerminalTheme.Background,
            surface = TerminalTheme.PanelBackground,
            primary = TerminalTheme.Accent,
            onBackground = TerminalTheme.Foreground,
            onSurface = TerminalTheme.Foreground,
        ),
    ) {
        GameScreen(game, effects, audio = audio)
    }
}
