package griffio.krogue.game

import kotlin.math.abs

/** Visual/elemental flavour of a spell or attack, picked up by the particle ramps. */
enum class SpellKind { FIRE, ENERGY, VENOM }

/**
 * A castable spell. [cost] is mana spent; [damage] is applied to what the bolt
 * hits; [aoeRadius] of 0 means single-target (the first monster on the line),
 * while a positive radius detonates at the impact and hits everything within
 * (Chebyshev) range.
 */
enum class Spell(
    val displayName: String,
    val cost: Int,
    val damage: Int,
    val kind: SpellKind,
    val aoeRadius: Int,
) {
    FIRE_BOLT("fire bolt", cost = 3, damage = 6, kind = SpellKind.FIRE, aoeRadius = 0),
    ENERGY_BLAST("energy blast", cost = 6, damage = 5, kind = SpellKind.ENERGY, aoeRadius = 2),
}

/**
 * The ordered cells a bolt passes through, from the origin to the target,
 * inclusive of both ends (Bresenham's line). Callers trace from index 1 to skip
 * the caster's own tile.
 */
fun lineOfFire(x0: Int, y0: Int, x1: Int, y1: Int): List<Pair<Int, Int>> {
    val points = mutableListOf<Pair<Int, Int>>()
    var x = x0
    var y = y0
    val dx = abs(x1 - x0)
    val dy = abs(y1 - y0)
    val sx = if (x0 < x1) 1 else -1
    val sy = if (y0 < y1) 1 else -1
    var err = dx - dy
    while (true) {
        points.add(x to y)
        if (x == x1 && y == y1) break
        val e2 = 2 * err
        if (e2 > -dy) { err -= dy; x += sx }
        if (e2 < dx) { err += dx; y += sy }
    }
    return points
}
