package griffio.krogue.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.hypot

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

    /** A spell projectile fired from one cell to an impact cell. */
    data class Bolt(
        override val x: Int,
        override val y: Int,
        val toX: Int,
        val toY: Int,
        val kind: SpellKind,
        val impactRadius: Int,
    ) : GameEvent

    /** A ranged monster winding up to fire — a brief pulse at its tile. */
    data class Charge(override val x: Int, override val y: Int, val kind: SpellKind) : GameEvent
}

/**
 * One animated thing drawn over the map. [ageNanos] is advanced by the frame
 * loop and compared against [lifeNanos] to fade and expire it; [fraction] is the
 * normalised progress used to drive position, colour, and alpha. Coordinates are
 * in *world cells* — the overlay maps them to pixels.
 */
sealed interface Particle {
    var ageNanos: Long
    val lifeNanos: Long
    val fraction: Float get() = (ageNanos.toFloat() / lifeNanos).coerceIn(0f, 1f)
}

/** A rising, fading number (combat damage, gold). */
class FloatingText(
    val worldX: Float,
    val worldY: Float,
    val text: String,
    val color: Color,
    override var ageNanos: Long,
    override val lifeNanos: Long,
) : Particle

/** A projectile glyph travelling a straight line, leaving a fading trail. */
class Bolt(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val kind: SpellKind,
    val impactRadius: Int,
    override var ageNanos: Long,
    override val lifeNanos: Long,
) : Particle

/** An expanding ring of glyphs at an impact point. */
class Burst(
    val centerX: Float,
    val centerY: Float,
    val maxRadius: Float,
    val kind: SpellKind,
    override var ageNanos: Long,
    override val lifeNanos: Long,
) : Particle

/**
 * The real-time effects channel that sits beside the turn-based [GameState].
 * [emit] is wired to `GameState.onEvent`; [advance] is driven by a
 * `withFrameNanos` loop in the UI. Adding a new effect means a new [GameEvent]
 * and a new [Particle] — the game core stays untouched.
 */
class EffectsState {

    val particles = mutableStateListOf<Particle>()

    /** Bumped every frame so a Canvas that reads it redraws while animating. */
    var tick: Long by mutableStateOf(0L)
        private set

    private var prevNanos = -1L

    fun emit(event: GameEvent) {
        when (event) {
            is GameEvent.Hit -> {
                val color = if (event.target == HitTarget.HERO) HeroDamage else MonsterDamage
                addText(event.x, event.y, "-${event.amount}", color)
            }
            is GameEvent.Gold -> addText(event.x, event.y, "+${event.amount}", GoldGain)
            is GameEvent.Bolt -> {
                val dist = hypot((event.toX - event.x).toFloat(), (event.toY - event.y).toFloat())
                val life = (dist * BOLT_NANOS_PER_CELL).toLong().coerceAtLeast(MIN_BOLT_NANOS)
                particles.add(
                    Bolt(
                        fromX = event.x + 0.5f,
                        fromY = event.y.toFloat(),
                        toX = event.toX + 0.5f,
                        toY = event.toY.toFloat(),
                        kind = event.kind,
                        impactRadius = event.impactRadius,
                        ageNanos = 0L,
                        lifeNanos = life,
                    ),
                )
            }
            is GameEvent.Charge ->
                particles.add(Burst(event.x + 0.5f, event.y.toFloat(), 0.8f, event.kind, 0L, CHARGE_NANOS))
        }
    }

    /** Advance the simulation to the given frame timestamp; reap and chain. */
    fun advance(frameTimeNanos: Long) {
        val prev = prevNanos
        prevNanos = frameTimeNanos
        if (prev != -1L) {
            val delta = frameTimeNanos - prev
            for (p in particles) p.ageNanos += delta
            val finished = particles.filter { it.ageNanos >= it.lifeNanos }
            if (finished.isNotEmpty()) {
                particles.removeAll(finished)
                // A bolt that lands detonates into a burst at its impact.
                for (p in finished) if (p is Bolt) {
                    val radius = if (p.impactRadius <= 0) 1f else p.impactRadius.toFloat()
                    particles.add(Burst(p.toX, p.toY, radius, p.kind, 0L, BURST_NANOS))
                }
            }
        }
        tick = frameTimeNanos
        // Idle: let the loop stop and re-baseline cleanly on the next burst.
        if (particles.isEmpty()) prevNanos = -1L
    }

    private fun addText(x: Int, y: Int, text: String, color: Color) {
        particles.add(FloatingText(x + 0.5f, y.toFloat(), text, color, ageNanos = 0L, lifeNanos = LIFE_NANOS))
    }

    companion object {
        const val LIFE_NANOS = 900_000_000L
        const val RISE_CELLS = 1.4f
        const val BURST_NANOS = 300_000_000L
        const val CHARGE_NANOS = 360_000_000L
        private const val BOLT_NANOS_PER_CELL = 26_000_000L
        private const val MIN_BOLT_NANOS = 90_000_000L

        private val HeroDamage = Color(0xFFE0604F)
        private val MonsterDamage = Color(0xFFFFF44F)
        private val GoldGain = Color(0xFFE0B24F)

        // Age-keyed colour ramps: t=0 is youngest (hottest), t=1 is spent.
        private val FireStops = listOf(
            Color(0xFFFFF6D0), Color(0xFFFFD23F), Color(0xFFFF8C1A), Color(0xFFE0341B), Color(0xFF5A4A45),
        )
        private val EnergyStops = listOf(
            Color(0xFFEAF6FF), Color(0xFF6FE0FF), Color(0xFF3A7BFF), Color(0xFF8A5CFF), Color(0xFF2A2A55),
        )
        private val VenomStops = listOf(
            Color(0xFFE8FFD0), Color(0xFFA6F23F), Color(0xFF4FD01A), Color(0xFF2C8C1B), Color(0xFF35452A),
        )

        /** Colour for a spell particle of [kind] at normalised age [t] (0..1). */
        fun spellColor(kind: SpellKind, t: Float): Color {
            val stops = when (kind) {
                SpellKind.FIRE -> FireStops
                SpellKind.ENERGY -> EnergyStops
                SpellKind.VENOM -> VenomStops
            }
            val scaled = t.coerceIn(0f, 1f) * (stops.size - 1)
            val i = scaled.toInt().coerceAtMost(stops.size - 2)
            return lerp(stops[i], stops[i + 1], scaled - i)
        }
    }
}
