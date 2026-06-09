package griffio.krogue

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import griffio.krogue.game.Direction
import griffio.krogue.game.EffectsState
import griffio.krogue.game.GameEvent
import griffio.krogue.game.GameState
import griffio.krogue.game.SpellKind
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
 * steps; when > 0 we also fire a spell bolt at a visible monster and step the
 * frame clock so the projectile/trail is captured mid-flight.
 */
fun main(args: Array<String>) {
    val out = args.firstOrNull() ?: "/tmp/krogue_render.png"
    val steps = args.getOrNull(1)?.toIntOrNull() ?: 0
    val width = 1100
    val height = 760

    if (steps <= 0) {
        renderApp(out, width, height)
        return
    }

    val effects = EffectsState()
    val game = GameState(Random(123)).apply { onEvent = effects::emit }
    val rng = Random(7)
    repeat(steps) { game.move(Direction.entries[rng.nextInt(4)]) }

    // Aim a fire bolt at the nearest visible foe (or straight ahead if none) so
    // the staged frame shows a travelling projectile with its trail.
    val foe = game.nearestVisibleMonster()
    val targetX = foe?.x ?: (game.heroX + 5)
    val targetY = foe?.y ?: game.heroY
    effects.emit(GameEvent.Bolt(game.heroX, game.heroY, targetX, targetY, SpellKind.FIRE, impactRadius = 1))

    val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
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
    try {
        // Step the frame clock ~30 ms/frame so the bolt advances along its path.
        var nanos = 0L
        var image = scene.render(nanos)
        repeat(4) {
            nanos += 30_000_000L
            image = scene.render(nanos)
        }
        val data = image.encodeToData() ?: error("failed to encode image")
        File(out).writeBytes(data.bytes)
        println("wrote screenshot: $out (${data.bytes.size} bytes)")
    } finally {
        scene.close()
    }
}

private fun renderApp(out: String, width: Int, height: Int) {
    val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) { App() }
    try {
        scene.render()
        val image = scene.render(16_000_000L)
        val data = image.encodeToData() ?: error("failed to encode image")
        File(out).writeBytes(data.bytes)
        println("wrote screenshot: $out (${data.bytes.size} bytes)")
    } finally {
        scene.close()
    }
}
