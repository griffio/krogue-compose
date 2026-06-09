package griffio.krogue.game

/**
 * The kind of terrain occupying a single map cell.
 *
 * [glyph] is the character drawn in the terminal grid; [opaque] blocks the
 * field-of-view shadowcast; [walkable] decides whether the hero may step onto
 * it. Glyphs mirror the classic roguelike palette used in `krogue-kotter`.
 */
enum class Terrain(val glyph: Char, val opaque: Boolean, val walkable: Boolean) {
    WALL('#', opaque = true, walkable = false),
    FLOOR('.', opaque = false, walkable = true),
    DOOR('+', opaque = true, walkable = true),
    WATER('~', opaque = false, walkable = true),
    TRAP('^', opaque = false, walkable = true),
    TREASURE('$', opaque = false, walkable = true),
    STAIRS('>', opaque = false, walkable = true),
    AMULET('*', opaque = false, walkable = true),
    EMPTY(' ', opaque = true, walkable = false);

    companion object {
        fun fromChar(c: Char): Terrain = when (c) {
            '#' -> WALL
            '.' -> FLOOR
            '+' -> DOOR
            '~' -> WATER
            '^' -> TRAP
            '$', '£' -> TREASURE
            '>' -> STAIRS
            '*' -> AMULET
            else -> EMPTY
        }
    }
}
