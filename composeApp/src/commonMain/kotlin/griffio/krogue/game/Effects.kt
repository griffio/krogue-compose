package griffio.krogue.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Who was struck — picks the colour of the floating number. */
enum class HitTarget { HERO, MONSTER }

/**
 * A semantic thing that just happened in the world, in grid coordinates. The
 * game core emits these; it knows nothing about pixels or frames. The UI's
 * effects layer turns them into something animated.
 */
sealed interface GameEvent {
    val x: Int
    val y: Int

    data class Hit(override val x: Int, override val y: Int, val amount: Int, val target: HitTarget) : GameEvent
    data class Gold(override val x: Int, override val y: Int, val amount: Int) : GameEvent
}

/**
 * One short-lived bit of floating text drawn over the map. Coordinates are in
 * *world cells* (the overlay maps them to pixels); [ageNanos] is advanced by the
 * frame loop and compared against [lifeNanos] to fade and expire it.
 */
class FloatingText(
    val worldX: Float,
    val worldY: Float,
    val text: String,
    val color: Color,
    var ageNanos: Long,
    val lifeNanos: Long,
)

/**
 * The real-time effects channel that sits beside the turn-based [GameState].
 * [emit] is wired to `GameState.onEvent`; [advance] is driven by a
 * `withFrameNanos` loop in the UI. This is deliberately the whole particle
 * system — later milestones add new [GameEvent] kinds and new visuals here
 * without touching the game core.
 */
class EffectsState {

    val particles = mutableStateListOf<FloatingText>()

    /** Bumped every frame so a Canvas that reads it redraws while animating. */
    var tick: Long by mutableStateOf(0L)
        private set

    private var prevNanos = -1L

    fun emit(event: GameEvent) {
        when (event) {
            is GameEvent.Hit -> {
                val color = if (event.target == HitTarget.HERO) HeroDamage else MonsterDamage
                add(event.x, event.y, "-${event.amount}", color)
            }
            is GameEvent.Gold -> add(event.x, event.y, "+${event.amount}", GoldGain)
        }
    }

    /** Advance the simulation to the given frame timestamp and reap dead particles. */
    fun advance(frameTimeNanos: Long) {
        val prev = prevNanos
        prevNanos = frameTimeNanos
        if (prev != -1L) {
            val delta = frameTimeNanos - prev
            for (p in particles) p.ageNanos += delta
            particles.removeAll { it.ageNanos >= it.lifeNanos }
        }
        tick = frameTimeNanos
        // Idle: let the loop stop and re-baseline cleanly on the next burst.
        if (particles.isEmpty()) prevNanos = -1L
    }

    private fun add(x: Int, y: Int, text: String, color: Color) {
        // Centre horizontally on the cell; start ageNanos relative so a particle
        // emitted between frames isn't aged by an absolute baseline.
        particles.add(FloatingText(x + 0.5f, y.toFloat(), text, color, ageNanos = 0L, lifeNanos = LIFE_NANOS))
    }

    companion object {
        const val LIFE_NANOS = 900_000_000L
        const val RISE_CELLS = 1.4f

        private val HeroDamage = Color(0xFFE0604F)
        private val MonsterDamage = Color(0xFFFFF44F)
        private val GoldGain = Color(0xFFE0B24F)
    }
}
