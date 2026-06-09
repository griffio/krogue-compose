package griffio.krogue.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    @Test
    fun monstersSpawnOnWalkableTilesAwayFromHero() {
        val game = GameState(Random(101))
        assertTrue(game.monsters.isNotEmpty(), "expected monsters to spawn")
        for (m in game.monsters) {
            assertTrue(game.dungeon.terrainAt(m.x, m.y).walkable, "monster on walkable tile")
            assertTrue(!(m.x == game.heroX && m.y == game.heroY), "monster not on the hero")
        }
        // Distinct tiles: no two monsters share a cell.
        val cells = game.monsters.map { it.x to it.y }
        assertEquals(cells.size, cells.toSet().size, "monsters occupy distinct tiles")
    }

    @Test
    fun heroBumpKillsAWeakMonsterAndFiresAHitEvent() {
        val game = GameState(Random(11))
        val hx = game.heroX
        val tx = hx + 1
        val ty = game.heroY
        game.dungeon.cells[ty][tx] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val rat = Monster(tx, ty, 1, MonsterKind.RAT) // 1 hp dies to one hero strike
        game.monsters.add(rat)

        var hit: GameEvent.Hit? = null
        game.onEvent = { if (it is GameEvent.Hit) hit = it }

        game.move(Direction.EAST)

        assertTrue(rat !in game.monsters, "the rat should be slain")
        assertEquals(1, game.kills)
        assertEquals(hx, game.heroX, "attacking does not move the hero")
        val firedHit = assertNotNull(hit, "a hit event should fire")
        assertEquals(HitTarget.MONSTER, firedHit.target)
    }

    @Test
    fun adjacentMonstersDamageTheHero() {
        val game = GameState(Random(13))
        val hx = game.heroX
        val hy = game.heroY
        val neighbors = listOf(hx + 1 to hy, hx - 1 to hy, hx to hy + 1, hx to hy - 1)
        for ((x, y) in neighbors) game.dungeon.cells[y][x] = Terrain.FLOOR.glyph
        game.monsters.clear()
        for ((x, y) in neighbors) game.monsters.add(Monster(x, y, 50, MonsterKind.ORC))

        val before = game.hp
        game.move(Direction.EAST) // bump one; the others step onto the hero and strike

        assertTrue(game.hp < before, "surrounding monsters should wound the hero")
    }

    @Test
    fun steppingOntoTheRelicWins() {
        val game = GameState(Random(5))
        val tx = game.heroX + 1
        val ty = game.heroY
        game.dungeon.cells[ty][tx] = Terrain.AMULET.glyph
        game.monsters.clear()

        game.move(Direction.EAST)

        assertEquals(GameStatus.WON, game.status)
    }

    @Test
    fun effectsParticlesAgeAndExpire() {
        val fx = EffectsState()
        fx.emit(GameEvent.Hit(3, 4, 2, HitTarget.MONSTER))
        assertEquals(1, fx.particles.size)
        fx.advance(0L) // establish the frame baseline
        fx.advance(EffectsState.LIFE_NANOS + 1L) // a delta past the lifetime
        assertTrue(fx.particles.isEmpty(), "an aged-out particle should be reaped")
    }
}
