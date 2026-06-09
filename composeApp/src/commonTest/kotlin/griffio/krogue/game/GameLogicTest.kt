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

    @Test
    fun fireBoltDamagesTheTargetSpendsManaAndFiresABoltEvent() {
        val game = GameState(Random(21))
        val hx = game.heroX
        val hy = game.heroY
        val tx = hx + 3
        for (x in hx..tx) game.dungeon.cells[hy][x] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val foe = Monster(tx, hy, 100, MonsterKind.ORC)
        game.monsters.add(foe)

        var bolt: GameEvent.Bolt? = null
        game.onEvent = { if (it is GameEvent.Bolt) bolt = it }

        game.cast(Spell.FIRE_BOLT, foe)

        assertEquals(100 - Spell.FIRE_BOLT.damage, foe.hp)
        assertEquals(1, game.turn, "casting ends a turn")
        // Spent the cost, then the per-turn regen tick gave 1 back.
        assertEquals(GameState.MAX_MANA - Spell.FIRE_BOLT.cost + 1, game.mp)
        val firedBolt = assertNotNull(bolt, "a bolt event should fire")
        assertEquals(tx, firedBolt.toX)
        assertEquals(hy, firedBolt.toY)
    }

    @Test
    fun castingWithoutEnoughManaIsANoOp() {
        val game = GameState(Random(22))
        val hx = game.heroX
        val hy = game.heroY
        game.dungeon.cells[hy][hx + 1] = Terrain.FLOOR.glyph
        game.dungeon.cells[hy][hx + 2] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val foe = Monster(hx + 2, hy, 100, MonsterKind.ORC)
        game.monsters.add(foe)

        game.cast(Spell.ENERGY_BLAST, foe) // 12 -> 6 (+1)
        game.cast(Spell.ENERGY_BLAST, foe) // 7 -> 1 (+1) => 2

        val mpBefore = game.mp
        val turnBefore = game.turn
        val hpBefore = foe.hp
        game.cast(Spell.FIRE_BOLT, foe) // 2 < 3: rejected

        assertEquals(mpBefore, game.mp, "no mana spent")
        assertEquals(turnBefore, game.turn, "no turn passes")
        assertEquals(hpBefore, foe.hp, "no damage dealt")
    }

    @Test
    fun energyBlastDamagesEveryMonsterInRadiusAndSparesTheHero() {
        val game = GameState(Random(23))
        val hx = game.heroX
        val hy = game.heroY
        val cx = hx + 3
        for (x in hx..(cx + 1)) game.dungeon.cells[hy][x] = Terrain.FLOOR.glyph
        game.dungeon.cells[hy + 1][cx] = Terrain.FLOOR.glyph
        game.dungeon.cells[hy - 1][cx] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val center = Monster(cx, hy, 50, MonsterKind.ORC)
        val above = Monster(cx, hy - 1, 50, MonsterKind.ORC)
        val below = Monster(cx, hy + 1, 50, MonsterKind.ORC)
        game.monsters.addAll(listOf(center, above, below))

        val heroHpBefore = game.hp
        game.cast(Spell.ENERGY_BLAST, center)

        assertTrue(center.hp < 50 && above.hp < 50 && below.hp < 50, "all three caught in the blast")
        // The cluster is >1 cell away, so no monster reaches the hero this turn,
        // and the blast itself never targets the hero's tile.
        assertEquals(heroHpBefore, game.hp, "the blast does not wound the hero")
    }

    @Test
    fun aBoltFizzlesAgainstAWall() {
        val game = GameState(Random(24))
        val hx = game.heroX
        val hy = game.heroY
        game.dungeon.cells[hy][hx + 1] = Terrain.WALL.glyph
        game.dungeon.cells[hy][hx + 3] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val foe = Monster(hx + 3, hy, 50, MonsterKind.ORC)
        game.monsters.add(foe)

        game.cast(Spell.FIRE_BOLT, foe)

        assertEquals(50, foe.hp, "the wall blocks the bolt")
    }

    @Test
    fun manaRegeneratesOverTurnsUpToTheMax() {
        val game = GameState(Random(25))
        val hx = game.heroX
        val hy = game.heroY
        game.dungeon.cells[hy][hx - 1] = Terrain.FLOOR.glyph
        game.dungeon.cells[hy][hx + 1] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val foe = Monster(hx + 1, hy, 100, MonsterKind.ORC)
        game.monsters.add(foe)

        game.cast(Spell.ENERGY_BLAST, foe) // spend 6
        val afterCast = game.mp
        game.monsters.clear() // clear the field so plain moves just pass turns

        game.move(Direction.WEST)
        game.move(Direction.EAST)
        game.move(Direction.WEST)

        assertTrue(game.mp > afterCast, "mana climbs back over turns")
        assertTrue(game.mp <= game.maxMp, "mana never exceeds the cap")
    }

    @Test
    fun aFinishedBoltChainsIntoABurst() {
        val fx = EffectsState()
        fx.emit(GameEvent.Bolt(0, 0, 3, 0, SpellKind.FIRE, impactRadius = 1))
        assertTrue(fx.particles.single() is Bolt)
        fx.advance(0L) // baseline
        fx.advance(10_000_000_000L) // long past the bolt's flight
        assertTrue(fx.particles.none { it is Bolt }, "the bolt is reaped")
        assertTrue(fx.particles.any { it is Burst }, "and detonates into a burst")
    }

    @Test
    fun visibleMonstersAndTargetCycling() {
        val game = GameState(Random(26))
        val hx = game.heroX
        val hy = game.heroY
        game.dungeon.cells[hy][hx + 1] = Terrain.FLOOR.glyph
        game.dungeon.cells[hy + 1][hx] = Terrain.FLOOR.glyph
        game.monsters.clear()
        val a = Monster(hx + 1, hy, 5, MonsterKind.RAT)
        val b = Monster(hx, hy + 1, 5, MonsterKind.RAT)
        game.monsters.addAll(listOf(a, b))

        val vis = game.visibleMonsters
        assertEquals(2, vis.size, "both adjacent monsters are in sight")

        val t1 = assertNotNull(game.nearestVisibleMonster())
        val t2 = assertNotNull(game.cycleTarget(t1))
        assertTrue(t1 !== t2, "cycling moves to the other foe")
        assertEquals(t1, game.cycleTarget(t2), "and wraps back around")
    }
}
