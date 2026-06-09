package griffio.krogue.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random

enum class GameStatus { PLAYING, WON, DEAD }

/** A single cardinal step the hero can take. */
enum class Direction(val dx: Int, val dy: Int) {
    NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0)
}

/**
 * The mutable, observable model for one play session.
 *
 * Terrain comes from the [DungeonGenerator]; [visible] / [explored] are
 * recomputed by [ShadowCast] after every move to drive the fog of war. Compose
 * snapshot state (the `by mutableStateOf` properties and the message list) lets
 * the UI recompose without an explicit frame counter; the boolean FOV grids are
 * plain arrays read during the same recomposition that a hero move triggers.
 */
class GameState(private val random: Random = Random.Default) {

    var dungeon: Dungeon by mutableStateOf(generateLevel())
        private set

    var heroX: Int by mutableStateOf(0)
        private set
    var heroY: Int by mutableStateOf(0)
        private set

    var maxHp: Int by mutableStateOf(20)
        private set
    var hp: Int by mutableStateOf(20)
        private set
    var maxMp: Int by mutableStateOf(MAX_MANA)
        private set
    var mp: Int by mutableStateOf(MAX_MANA)
        private set
    var gold: Int by mutableStateOf(0)
        private set
    var depth: Int by mutableStateOf(1)
        private set
    var turn: Int by mutableStateOf(0)
        private set
    var kills: Int by mutableStateOf(0)
        private set
    var status: GameStatus by mutableStateOf(GameStatus.PLAYING)
        private set

    val messages = mutableStateListOf<String>()

    /** The monsters on the current level. */
    val monsters = mutableStateListOf<Monster>()

    /**
     * Sink for in-world events (hits, pickups). The UI's effects layer wires
     * this up; the core stays unaware of how — or whether — they're animated.
     */
    var onEvent: ((GameEvent) -> Unit)? = null

    /** The hero's per-hit melee damage, growing slightly with depth. */
    private val heroDamage get() = 4 + depth / 2

    var visible: Array<BooleanArray> = emptyGrid()
        private set
    var explored: Array<BooleanArray> = emptyGrid()
        private set

    init {
        startNewGame()
    }

    val width get() = dungeon.width
    val height get() = dungeon.height

    fun isVisible(x: Int, y: Int) =
        y in visible.indices && x in visible[y].indices && visible[y][x]

    fun isExplored(x: Int, y: Int) =
        y in explored.indices && x in explored[y].indices && explored[y][x]

    fun monsterAt(x: Int, y: Int): Monster? = monsters.firstOrNull { it.x == x && it.y == y }

    /** Monsters the hero can currently see, the only valid spell targets. */
    val visibleMonsters: List<Monster> get() = monsters.filter { isVisible(it.x, it.y) }

    /** The closest visible monster to the hero, or null if none are in sight. */
    fun nearestVisibleMonster(): Monster? =
        visibleMonsters.minByOrNull { abs(it.x - heroX) + abs(it.y - heroY) }

    /** The next visible monster after [after], wrapping around; for Tab-cycling. */
    fun cycleTarget(after: Monster?): Monster? {
        val vis = visibleMonsters
        if (vis.isEmpty()) return null
        val idx = vis.indexOf(after)
        return vis[(idx + 1) % vis.size]
    }

    fun canCast(spell: Spell): Boolean = status == GameStatus.PLAYING && mp >= spell.cost

    /**
     * Cast [spell] at [target]. Resolves instantly: trace the line of fire to the
     * first wall (a fizzle) or first monster, apply damage (single-target, or
     * everything within the spell's radius for an area blast), spend mana, and
     * emit the bolt event for the effects layer to animate. Then the turn ends.
     */
    fun cast(spell: Spell, target: Monster) {
        if (status != GameStatus.PLAYING) return
        if (mp < spell.cost) {
            log("Not enough mana for ${spell.displayName}.")
            return
        }
        mp -= spell.cost

        val (impactX, impactY, struck) = trace(target.x, target.y)
        onEvent?.invoke(GameEvent.Bolt(heroX, heroY, impactX, impactY, spell.kind, spell.aoeRadius))

        if (spell.aoeRadius > 0) {
            val caught = monsters.filter {
                abs(it.x - impactX) <= spell.aoeRadius && abs(it.y - impactY) <= spell.aoeRadius
            }
            if (caught.isEmpty()) {
                log("Your ${spell.displayName} scorches empty stone.")
            } else {
                log("Your ${spell.displayName} erupts!")
                for (m in caught) damageMonster(m, spell.damage)
            }
        } else if (struck != null) {
            log("Your ${spell.displayName} strikes the ${struck.kind.displayName}.")
            damageMonster(struck, spell.damage)
        } else {
            log("Your ${spell.displayName} fizzles against the wall.")
        }

        turn++
        endTurn()
    }

    /**
     * Walk the bolt's path from the hero toward (tx, ty), stopping at the first
     * monster (returned) or the first opaque wall. Returns the impact cell and
     * the monster hit, if any.
     */
    private fun trace(tx: Int, ty: Int): Triple<Int, Int, Monster?> {
        val path = lineOfFire(heroX, heroY, tx, ty)
        for (i in 1 until path.size) {
            val (x, y) = path[i]
            monsterAt(x, y)?.let { return Triple(x, y, it) }
            if (dungeon.terrainAt(x, y).opaque) return Triple(x, y, null)
        }
        return Triple(tx, ty, monsterAt(tx, ty))
    }

    fun startNewGame() {
        depth = 1
        maxHp = 20
        hp = 20
        maxMp = MAX_MANA
        mp = MAX_MANA
        gold = 0
        turn = 0
        kills = 0
        status = GameStatus.PLAYING
        messages.clear()
        enterLevel(generateLevel())
        log("Welcome to the dungeon. Fight down to depth $MAX_DEPTH and seize the relic.")
    }

    fun move(direction: Direction) {
        if (status != GameStatus.PLAYING) return
        val targetX = heroX + direction.dx
        val targetY = heroY + direction.dy

        // A monster in the way: bump it to attack instead of moving onto it.
        val foe = monsterAt(targetX, targetY)
        if (foe != null) {
            heroAttack(foe)
            turn++
            endTurn()
            return
        }

        val terrain = dungeon.terrainAt(targetX, targetY)
        if (!terrain.walkable) {
            // A bump against a wall passing time would be punishing; treat it as a
            // no-op so exploration stays forgiving.
            return
        }

        heroX = targetX
        heroY = targetY
        turn++

        when (terrain) {
            Terrain.TREASURE -> {
                gold++
                dungeon.cells[targetY][targetX] = Terrain.FLOOR.glyph
                onEvent?.invoke(GameEvent.Gold(targetX, targetY, 1))
                log("You pick up gold. ($gold collected)")
            }
            Terrain.WATER -> {
                damageHero(1, targetX, targetY)
                log("You wade through cold water. (-1 hp)")
            }
            Terrain.TRAP -> {
                damageHero(3, targetX, targetY)
                log("A trap springs! (-3 hp)")
            }
            Terrain.STAIRS -> {
                descend()
                return
            }
            Terrain.AMULET -> {
                status = GameStatus.WON
                log("You seize the relic! Victory. Press R to play again.")
                recomputeFov()
                return
            }
            else -> {}
        }

        endTurn()
    }

    /** Resolve one hero melee strike against [foe]. */
    private fun heroAttack(foe: Monster) {
        val dmg = heroDamage
        val killed = damageMonster(foe, dmg)
        if (!killed) log("You hit the ${foe.kind.displayName}. (-$dmg)")
    }

    /**
     * Apply [dmg] to [monster], floating the number and reaping it on death.
     * Shared by melee and spells. Returns true if the monster died.
     */
    private fun damageMonster(monster: Monster, dmg: Int): Boolean {
        monster.hp -= dmg
        onEvent?.invoke(GameEvent.Hit(monster.x, monster.y, dmg, HitTarget.MONSTER))
        if (monster.hp <= 0) {
            monsters.remove(monster)
            kills++
            log("The ${monster.kind.displayName} is destroyed.")
            return true
        }
        return false
    }

    private fun damageHero(amount: Int, x: Int, y: Int) {
        hp = (hp - amount).coerceAtLeast(0)
        onEvent?.invoke(GameEvent.Hit(x, y, amount, HitTarget.HERO))
    }

    /** Monsters act, mana trickles back, then we settle death and visibility. */
    private fun endTurn() {
        mp = (mp + MANA_REGEN).coerceAtMost(maxMp)
        if (hp <= 0) {
            die()
            recomputeFov()
            return
        }
        monstersAct()
        if (hp <= 0) die()
        recomputeFov()
    }

    /**
     * Every monster takes one step. A monster the hero can see (its tile is lit
     * by the shared FOV) chases along the dominant axis, falling back to the
     * other axis if the first is blocked; an unseen monster wanders. Stepping
     * onto the hero is a bump-attack instead of a move.
     */
    private fun monstersAct() {
        // Snapshot: a monster never dies during its own turn, but iterate a copy
        // so the live list can be mutated safely if that changes later.
        for (monster in monsters.toList()) {
            if (status != GameStatus.PLAYING) break
            val steps = if (isVisible(monster.x, monster.y)) chaseSteps(monster) else listOf(wanderStep())
            for ((sx, sy) in steps) {
                if (sx == 0 && sy == 0) continue
                val nx = monster.x + sx
                val ny = monster.y + sy
                if (nx == heroX && ny == heroY) {
                    val dmg = monster.kind.attack
                    hp = (hp - dmg).coerceAtLeast(0)
                    onEvent?.invoke(GameEvent.Hit(heroX, heroY, dmg, HitTarget.HERO))
                    log("The ${monster.kind.displayName} hits you. (-$dmg)")
                    break
                }
                if (canMonsterEnter(nx, ny)) {
                    monster.x = nx
                    monster.y = ny
                    break
                }
            }
        }
    }

    /** Candidate steps toward the hero, dominant axis first. */
    private fun chaseSteps(monster: Monster): List<Pair<Int, Int>> {
        val dx = (heroX - monster.x).signum()
        val dy = (heroY - monster.y).signum()
        val horizontalFirst = abs(heroX - monster.x) >= abs(heroY - monster.y)
        val primary = if (horizontalFirst) dx to 0 else 0 to dy
        val secondary = if (horizontalFirst) 0 to dy else dx to 0
        return listOf(primary, secondary)
    }

    private fun wanderStep(): Pair<Int, Int> {
        val dir = Direction.entries[random.nextInt(Direction.entries.size)]
        return dir.dx to dir.dy
    }

    private fun canMonsterEnter(x: Int, y: Int): Boolean =
        dungeon.terrainAt(x, y).walkable && monsterAt(x, y) == null && !(x == heroX && y == heroY)

    private fun die() {
        status = GameStatus.DEAD
        log("You collapse in the dark. Press R to try again.")
    }

    private fun descend() {
        depth++
        // A small reward for pressing deeper.
        maxHp += 2
        hp = (hp + 5).coerceAtMost(maxHp)
        log("You descend to depth $depth.")
        enterLevel(generateLevel())
    }

    private fun generateLevel(): Dungeon = DungeonGenerator(random).generate()

    private fun enterLevel(level: Dungeon) {
        dungeon = level
        visible = Array(level.height) { BooleanArray(level.width) }
        explored = Array(level.height) { BooleanArray(level.width) }
        val spawn = level.rooms.firstOrNull()
        heroX = spawn?.centerX ?: level.width / 2
        heroY = spawn?.centerY ?: level.height / 2

        // On the deepest floor the down-stairs become the relic: the goal.
        if (depth >= MAX_DEPTH && level.stairsX >= 0) {
            level.cells[level.stairsY][level.stairsX] = Terrain.AMULET.glyph
        }

        monsters.clear()
        monsters.addAll(spawnMonsters(level, depth, random, heroX, heroY))
        recomputeFov()
    }

    private fun recomputeFov() {
        for (row in visible) row.fill(false)
        ShadowCast.compute(
            originX = heroX,
            originY = heroY,
            radius = FOV_RADIUS,
            width = width,
            height = height,
            isOpaque = { x, y -> dungeon.terrainAt(x, y).opaque },
            setVisible = { x, y ->
                visible[y][x] = true
                explored[y][x] = true
            },
        )
    }

    /** Push a UI-originated message (e.g. "no target") into the same log stream. */
    fun announce(message: String) = log(message)

    private fun log(message: String) {
        messages.add(message)
        if (messages.size > MAX_MESSAGES) messages.removeAt(0)
    }

    private fun emptyGrid() = Array(1) { BooleanArray(1) }

    /** Multiplatform integer signum (kotlin.math.sign is Float/Double only). */
    private fun Int.signum(): Int = when {
        this > 0 -> 1
        this < 0 -> -1
        else -> 0
    }

    companion object {
        const val FOV_RADIUS = 8
        const val MAX_DEPTH = 5
        const val MAX_MANA = 12
        private const val MANA_REGEN = 1
        private const val MAX_MESSAGES = 50
    }
}
