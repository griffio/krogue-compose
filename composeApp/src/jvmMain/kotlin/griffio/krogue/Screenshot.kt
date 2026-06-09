package griffio.krogue

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import griffio.krogue.game.Direction
import griffio.krogue.game.EffectsState
import griffio.krogue.game.GameEvent
import griffio.krogue.game.GameState
import griffio.krogue.game.HitTarget
import griffio.krogue.ui.GameScreen
import griffio.krogue.ui.TerminalTheme
import java.io.File
import kotlin.random.Random

/**
 * Headless render of the UI to a PNG, used to verify it without opening a
 * window (no display/accessibility permissions required). Run via the
 * `renderScreenshot` Gradle task.
 *
 * args[0] = output path. args[1] = optional number of scripted random-walk
 * steps to take before rendering, which demonstrates fog-of-war memory.
 */
fun main(args: Array<String>) {
    val out = args.firstOrNull() ?: "/tmp/krogue_render.png"
    val steps = args.getOrNull(1)?.toIntOrNull() ?: 0
    val width = 1100
    val height = 760
    val scene = ImageComposeScene(
        width = width,
        height = height,
        density = Density(1f),
    ) {
        if (steps > 0) {
            val effects = EffectsState()
            val game = GameState(Random(123)).apply { onEvent = effects::emit }
            val rng = Random(7)
            repeat(steps) { game.move(Direction.entries[rng.nextInt(4)]) }
            // Particles are transient and won't survive a random walk, so emit a
            // demo hit at the hero just before rendering to capture the overlay.
            effects.emit(GameEvent.Hit(game.heroX, game.heroY, 3, HitTarget.HERO))
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
        } else {
            App()
        }
    }
    try {
        // Render a couple of frames so initial layout / FOV settles.
        scene.render()
        val image = scene.render(16_000_000L)
        val data = image.encodeToData() ?: error("failed to encode image")
        File(out).writeBytes(data.bytes)
        println("wrote screenshot: $out (${data.bytes.size} bytes)")
    } finally {
        scene.close()
    }
}
