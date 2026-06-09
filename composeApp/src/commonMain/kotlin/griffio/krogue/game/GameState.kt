package griffio.krogue.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    var gold: Int by mutableStateOf(0)
        private set
    var depth: Int by mutableStateOf(1)
        private set
    var turn: Int by mutableStateOf(0)
        private set
    var status: GameStatus by mutableStateOf(GameStatus.PLAYING)
        private set

    val messages = mutableStateListOf<String>()

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

    fun startNewGame() {
        depth = 1
        maxHp = 20
        hp = 20
        gold = 0
        turn = 0
        status = GameStatus.PLAYING
        messages.clear()
        enterLevel(generateLevel())
        log("Welcome to the dungeon. Find the treasure, then the stairs down.")
    }

    fun move(direction: Direction) {
        if (status != GameStatus.PLAYING) return
        val targetX = heroX + direction.dx
        val targetY = heroY + direction.dy
        val terrain = dungeon.terrainAt(targetX, targetY)

        if (!terrain.walkable) {
            // A bump against a wall still passes time would be punishing; treat it
            // as a no-op so exploration stays forgiving.
            return
        }

        heroX = targetX
        heroY = targetY
        turn++

        when (terrain) {
            Terrain.TREASURE -> {
                gold++
                dungeon.cells[targetY][targetX] = Terrain.FLOOR.glyph
                log("You pick up gold. ($gold collected)")
            }
            Terrain.WATER -> {
                hp = (hp - 1).coerceAtLeast(0)
                log("You wade through cold water. (-1 hp)")
            }
            Terrain.TRAP -> {
                hp = (hp - 3).coerceAtLeast(0)
                log("A trap springs! (-3 hp)")
            }
            Terrain.STAIRS -> {
                descend()
            }
            else -> {}
        }

        if (hp <= 0) {
            status = GameStatus.DEAD
            log("You collapse in the dark. Press R to try again.")
        }

        recomputeFov()
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

    private fun log(message: String) {
        messages.add(message)
        if (messages.size > MAX_MESSAGES) messages.removeAt(0)
    }

    private fun emptyGrid() = Array(1) { BooleanArray(1) }

    companion object {
        const val FOV_RADIUS = 8
        private const val MAX_MESSAGES = 50
    }
}
