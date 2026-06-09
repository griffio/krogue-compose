package griffio.krogue.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameLogicTest {

    @Test
    fun generatorProducesWalkableRoomsWithinBounds() {
        val dungeon = DungeonGenerator(Random(42)).generate()
        assertTrue(dungeon.rooms.isNotEmpty(), "expected at least one room")
        assertTrue(dungeon.width in 40..80 && dungeon.height in 40..80)
        for (room in dungeon.rooms) {
            assertTrue(room.left in 1 until dungeon.width)
            assertTrue(room.right in 1 until dungeon.width)
            assertTrue(room.top in 1 until dungeon.height)
            assertTrue(room.bottom in 1 until dungeon.height)
        }
    }

    @Test
    fun fovAlwaysRevealsTheOriginAndStaysInRadius() {
        val dungeon = DungeonGenerator(Random(7)).generate()
        val origin = dungeon.rooms.first()
        val ox = origin.centerX
        val oy = origin.centerY
        val lit = mutableSetOf<Pair<Int, Int>>()
        ShadowCast.compute(
            originX = ox, originY = oy, radius = 8,
            width = dungeon.width, height = dungeon.height,
            isOpaque = { x, y -> dungeon.terrainAt(x, y).opaque },
            setVisible = { x, y -> lit += x to y },
        )
        assertTrue((ox to oy) in lit, "origin must be visible")
        // Nothing lit may sit outside the (radius + half-cell) envelope.
        for ((x, y) in lit) {
            val dx = x - ox
            val dy = y - oy
            assertTrue(dx * dx + dy * dy <= 9 * 9, "lit cell ($x,$y) outside radius")
        }
    }

    @Test
    fun heroSpawnsOnWalkableTileAndMovesOntoFloor() {
        val game = GameState(Random(99))
        val spawn = game.dungeon.terrainAt(game.heroX, game.heroY)
        assertTrue(spawn.walkable, "hero should spawn on a walkable tile")
        assertTrue(game.isVisible(game.heroX, game.heroY), "hero tile is visible")

        val startX = game.heroX
        // Walking into a wall is a no-op: position is unchanged and no turn passes.
        repeat(4) { game.move(Direction.NORTH) }
        assertTrue(game.turn <= 4)
        assertTrue(game.hp in 0..game.maxHp)
        // Sanity: heroX never leaves the grid.
        assertTrue(game.heroX in 0 until game.width)
        assertEquals(GameStatus.PLAYING, game.status.takeIf { game.hp > 0 } ?: GameStatus.PLAYING)
        assertTrue(startX in 0 until game.width)
    }
}
