package griffio.krogue.game

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** A rectangular room expressed in inclusive grid coordinates. */
data class Room(val top: Int, val bottom: Int, val left: Int, val right: Int) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
}

/** The result of one map generation: a char grid plus the rooms that were dug. */
data class Dungeon(
    val width: Int,
    val height: Int,
    val cells: Array<CharArray>,
    val rooms: List<Room>,
    val stairsX: Int = -1,
    val stairsY: Int = -1,
) {
    operator fun get(x: Int, y: Int): Char = cells[y][x]

    fun terrainAt(x: Int, y: Int): Terrain =
        if (y in 0 until height && x in 0 until width) Terrain.fromChar(cells[y][x]) else Terrain.EMPTY

    // Array members get reference equality by default; data classes warn without
    // these overrides. The grid is treated as a value for our purposes.
    override fun equals(other: Any?): Boolean =
        this === other || (other is Dungeon && width == other.width && height == other.height &&
            cells.contentDeepEquals(other.cells))

    override fun hashCode(): Int = 31 * (31 * width + height) + cells.contentDeepHashCode()
}

/**
 * Grows a connected dungeon by repeatedly attaching new rooms to existing ones
 * and carving a one-tile corridor between them.
 *
 * Ported from the `krogue-kotter` generator (itself after pushcx/ironwood),
 * refactored to be free of global mutable state and to take an injected
 * [Random] so generation is deterministic and testable.
 */
class DungeonGenerator(private val random: Random = Random.Default) {

    fun generate(): Dungeon {
        val width = SIZE_RANGE.random(random)
        val height = SIZE_RANGE.random(random)
        val cells = Array(height) { CharArray(width) { WALL } }
        val rooms = mutableListOf<Room>()

        fun digRoom(top: Int, bottom: Int, left: Int, right: Int): Boolean {
            // Refuse to dig if it would overwrite an already-carved cell, keeping
            // rooms from overlapping.
            for (y in top..bottom) for (x in left..right) {
                if (cells[y][x] != WALL) return false
            }
            for (y in top..bottom) for (x in left..right) {
                cells[y][x] = FLOOR
            }
            rooms += Room(top, bottom, left, right)
            return true
        }

        // Seed the first room somewhere in the interior.
        run {
            val top = (1..height - MIN_DIM - 2).random(random)
            val bottom = top + (MIN_DIM..min(MAX_DIM, height - top - 2)).random(random)
            val left = (1..width - MIN_DIM - 2).random(random)
            val right = left + (MIN_DIM..min(MAX_DIM, width - left - 2)).random(random)
            digRoom(top, bottom, left, right)
        }

        val attempts = (40..350).random(random)
        repeat(attempts) {
            val from = rooms.random(random)
            val distance = ROOM_DISTANCES.random(random)
            when ((0..3).random(random)) {
                0 -> growNorth(cells, width, height, from, distance, ::digRoom)
                1 -> growSouth(cells, width, height, from, distance, ::digRoom)
                2 -> growWest(cells, width, height, from, distance, ::digRoom)
                3 -> growEast(cells, width, height, from, distance, ::digRoom)
            }
        }

        scatterTreasure(cells, rooms)
        val stairs = placeStairs(cells, rooms)
        scatterHazards(cells, rooms)
        return Dungeon(width, height, cells, rooms, stairs?.first ?: -1, stairs?.second ?: -1)
    }

    private fun growNorth(
        cells: Array<CharArray>, width: Int, height: Int, from: Room, distance: Int,
        dig: (Int, Int, Int, Int) -> Boolean,
    ) {
        val bottom = from.top - distance
        if (bottom - MIN_DIM <= 0) return
        val h = (MIN_DIM..min(MAX_DIM, bottom - 1)).random(random)
        val top = bottom - h
        val w = (MIN_DIM..MAX_DIM).random(random)
        val left = (max(1, from.left - w)..min(width - w - 2, from.right)).random(random)
        val right = left + w
        if (!dig(top, bottom, left, right)) return
        val x = (max(left, from.left)..min(right, from.right)).random(random)
        for (y in bottom..from.top) cells[y][x] = FLOOR
        maybeDoor(cells, from.top - bottom, x, bottom + 1)
    }

    private fun growSouth(
        cells: Array<CharArray>, width: Int, height: Int, from: Room, distance: Int,
        dig: (Int, Int, Int, Int) -> Boolean,
    ) {
        val top = from.bottom + distance
        val h = (MIN_DIM..MAX_DIM).random(random)
        val bottom = top + h
        if (bottom >= height - 1) return
        val w = (MIN_DIM..MAX_DIM).random(random)
        val left = (max(1, from.left - w)..min(width - w - 2, from.right)).random(random)
        val right = left + w
        if (!dig(top, bottom, left, right)) return
        val x = (max(left, from.left)..min(right, from.right)).random(random)
        for (y in from.bottom..top) cells[y][x] = FLOOR
        maybeDoor(cells, top - from.bottom, x, top - 1)
    }

    private fun growWest(
        cells: Array<CharArray>, width: Int, height: Int, from: Room, distance: Int,
        dig: (Int, Int, Int, Int) -> Boolean,
    ) {
        val right = from.left - distance
        val w = (MIN_DIM..MAX_DIM).random(random)
        val left = right - w
        if (left <= 0) return
        val h = (MIN_DIM..MAX_DIM).random(random)
        val top = (max(1, from.top - h)..min(height - h - 2, from.bottom)).random(random)
        val bottom = top + h
        if (!dig(top, bottom, left, right)) return
        val y = (max(top, from.top)..min(bottom, from.bottom)).random(random)
        for (x in right..from.left) cells[y][x] = FLOOR
    }

    private fun growEast(
        cells: Array<CharArray>, width: Int, height: Int, from: Room, distance: Int,
        dig: (Int, Int, Int, Int) -> Boolean,
    ) {
        val left = from.right + distance
        val w = (MIN_DIM..MAX_DIM).random(random)
        val right = left + w
        if (right >= width - 1) return
        val h = (MIN_DIM..MAX_DIM).random(random)
        val top = (max(1, from.top - h)..min(height - h - 2, from.bottom)).random(random)
        val bottom = top + h
        if (!dig(top, bottom, left, right)) return
        val y = (max(top, from.top)..min(bottom, from.bottom)).random(random)
        for (x in from.right..left) cells[y][x] = FLOOR
    }

    /** A two-tile-long corridor reads as a doorway: drop a door glyph in it. */
    private fun maybeDoor(cells: Array<CharArray>, gap: Int, x: Int, y: Int) {
        if (gap == 2 && cells[y][x - 1] == WALL && cells[y][x + 1] == WALL) {
            cells[y][x] = DOOR
        }
    }

    private fun scatterTreasure(cells: Array<CharArray>, rooms: List<Room>) {
        if (rooms.isEmpty()) return
        repeat(TREASURE_COUNT) {
            val room = rooms.random(random)
            cells[room.centerY][room.centerX] = TREASURE
        }
    }

    /**
     * Activate the dormant hazard terrain: traps (and the odd pool of water) on
     * the floor tiles flanking [DOOR] thresholds, where the hero is funnelled,
     * plus a small base scatter so even a door-less map bites. Never overwrites
     * the spawn (first room's centre), stairs, or treasure — only plain floor.
     */
    private fun scatterHazards(cells: Array<CharArray>, rooms: List<Room>) {
        if (rooms.isEmpty()) return
        val height = cells.size
        val width = cells[0].size
        val spawn = rooms.first()

        fun placeable(x: Int, y: Int): Boolean =
            y in 0 until height && x in 0 until width && cells[y][x] == FLOOR &&
                !(x == spawn.centerX && y == spawn.centerY)

        for (y in 0 until height) for (x in 0 until width) {
            if (cells[y][x] != DOOR) continue
            for ((dx, dy) in DOOR_NEIGHBOURS) {
                val nx = x + dx
                val ny = y + dy
                if (placeable(nx, ny) && random.nextInt(100) < DOOR_TRAP_PERCENT) {
                    cells[ny][nx] = if (random.nextInt(4) == 0) WATER else TRAP
                }
            }
        }

        repeat(BASE_TRAP_COUNT) {
            val room = rooms.random(random)
            val x = (room.left..room.right).random(random)
            val y = (room.top..room.bottom).random(random)
            if (placeable(x, y)) cells[y][x] = TRAP
        }
    }

    private fun placeStairs(cells: Array<CharArray>, rooms: List<Room>): Pair<Int, Int>? {
        if (rooms.size < 2) return null
        // Put the descent in the room farthest (by center) from the first room.
        val start = rooms.first()
        val farthest = rooms.maxBy {
            val dx = it.centerX - start.centerX
            val dy = it.centerY - start.centerY
            dx * dx + dy * dy
        }
        cells[farthest.centerY][farthest.centerX] = STAIRS
        return farthest.centerX to farthest.centerY
    }

    companion object {
        private const val WALL = '#'
        private const val FLOOR = '.'
        private const val DOOR = '+'
        private const val TREASURE = '$'
        private const val STAIRS = '>'
        private const val TRAP = '^'
        private const val WATER = '~'
        private const val MIN_DIM = 2
        private const val MAX_DIM = 9
        private const val TREASURE_COUNT = 10
        private const val DOOR_TRAP_PERCENT = 55
        private const val BASE_TRAP_COUNT = 4
        private val DOOR_NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
        private val SIZE_RANGE = 48..72
        private val ROOM_DISTANCES = intArrayOf(1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 5)
    }
}
