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

    /** Trap cells the hero has spotted (been adjacent to); the rest stay hidden. */
    private val revealedTraps = mutableStateListOf<Pair<Int, Int>>()

    /**
     * Sink for in-world events (hits, pickups). The UI's effects layer wires
     * this up; the core stays unaware of how — or whether — they're animated.
     */
    var onEvent: ((GameEvent) -> Unit)? = null

    /** The hero's per-hit melee damage, growing slightly with depth. */
    private val heroDamage get() = 4 + depth / 2

    /** Counts turns toward the next point of mana regeneration. */
    private var manaTick = 0

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

    /** A trap tile the hero hasn't yet spotted — drawn as plain floor by the UI. */
    fun isHiddenTrap(x: Int, y: Int): Boolean =
        dungeon.terrainAt(x, y) == Terrain.TRAP && (x to y) !in revealedTraps

    /** Reveal any trap within one cell of the hero, the first time it's seen. */
    private fun revealNearbyTraps() {
        for (dy in -1..1) for (dx in -1..1) {
            val x = heroX + dx
            val y = heroY + dy
            if (dungeon.terrainAt(x, y) == Terrain.TRAP && (x to y) !in revealedTraps) {
                revealedTraps.add(x to y)
                log("You spot a trap.")
            }
        }
    }

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
        manaTick = 0
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
        monster.awake = true // a struck monster is wide awake
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
        // Mana trickles back slowly — one point every few turns — so spells stay a
        // resource to ration rather than a tap that refills between every fight.
        if (++manaTick >= MANA_REGEN_INTERVAL) {
            manaTick = 0
            mp = (mp + 1).coerceAtMost(maxMp)
        }
        revealNearbyTraps()
        // Refresh the hero's sight for their new position before monsters react,
        // so a monster steps into view the same turn the hero rounds a corner.
        // FOV depends only on hero position, so the rendered result is unchanged.
        recomputeFov()
        if (hp <= 0) {
            die()
            return
        }
        monstersAct()
        if (hp <= 0) die()
    }

    /**
     * Every monster takes a turn. A sleeper does nothing until it notices the
     * hero (lit by the shared FOV and within [WAKE_RADIUS]); once awake it either
     * chases to bump-attack, or — if ranged — telegraphs and fires.
     */
    private fun monstersAct() {
        // Snapshot: iterate a copy so the live list can be mutated safely.
        for (monster in monsters.toList()) {
            if (status != GameStatus.PLAYING) break
            if (!monster.awake) {
                if (isVisible(monster.x, monster.y) && chebyshev(monster.x, monster.y) <= WAKE_RADIUS) {
                    monster.awake = true
                    log("The ${monster.kind.displayName} notices you!")
                } else {
                    continue
                }
            }
            if (monster.kind.range > 0) rangedTurn(monster) else meleeTurn(monster)
        }
    }

    /**
     * A ranged monster telegraphs before firing: one turn winding up, the next
     * loosing a bolt. Stepping adjacent disrupts it (it melees instead); breaking
     * line of sight cancels the shot, giving the hero a way to dodge.
     */
    private fun rangedTurn(monster: Monster) {
        val cheb = chebyshev(monster.x, monster.y)
        if (monster.aiming) {
            monster.aiming = false
            when {
                cheb == 1 -> meleeAttack(monster)
                cheb <= monster.kind.range && hasLineOfSight(monster.x, monster.y, heroX, heroY) ->
                    fireAtHero(monster)
                else -> meleeTurn(monster)
            }
            return
        }
        if (cheb in 2..monster.kind.range && hasLineOfSight(monster.x, monster.y, heroX, heroY)) {
            monster.aiming = true
            onEvent?.invoke(GameEvent.Charge(monster.x, monster.y, SpellKind.VENOM))
            log("The ${monster.kind.displayName} gathers a sickly glow…")
        } else {
            meleeTurn(monster)
        }
    }

    private fun fireAtHero(monster: Monster) {
        val dmg = monster.kind.attack
        hp = (hp - dmg).coerceAtLeast(0)
        onEvent?.invoke(GameEvent.Bolt(monster.x, monster.y, heroX, heroY, SpellKind.VENOM, 0))
        onEvent?.invoke(GameEvent.Hit(heroX, heroY, dmg, HitTarget.HERO))
        log("The ${monster.kind.displayName} spits venom. (-$dmg)")
    }

    /** Chase the hero (or wander if unseen), bumping into the hero to attack. */
    private fun meleeTurn(monster: Monster) {
        val steps = if (isVisible(monster.x, monster.y)) chaseSteps(monster) else listOf(wanderStep())
        for ((sx, sy) in steps) {
            if (sx == 0 && sy == 0) continue
            val nx = monster.x + sx
            val ny = monster.y + sy
            if (nx == heroX && ny == heroY) {
                meleeAttack(monster)
                return
            }
            if (canMonsterEnter(nx, ny)) {
                monster.x = nx
                monster.y = ny
                return
            }
        }
    }

    private fun meleeAttack(monster: Monster) {
        val dmg = monster.kind.attack
        hp = (hp - dmg).coerceAtLeast(0)
        onEvent?.invoke(GameEvent.Hit(heroX, heroY, dmg, HitTarget.HERO))
        log("The ${monster.kind.displayName} hits you. (-$dmg)")
    }

    /** True if no opaque tile sits strictly between the two cells. */
    private fun hasLineOfSight(x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
        val path = lineOfFire(x0, y0, x1, y1)
        for (i in 1 until path.size - 1) {
            val (x, y) = path[i]
            if (dungeon.terrainAt(x, y).opaque) return false
        }
        return true
    }

    private fun chebyshev(x: Int, y: Int): Int = maxOf(abs(x - heroX), abs(y - heroY))

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
        revealedTraps.clear()
        recomputeFov()
        revealNearbyTraps()
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
        const val WAKE_RADIUS = 6
        private const val MANA_REGEN_INTERVAL = 3
        private const val MAX_MESSAGES = 50
    }
}
