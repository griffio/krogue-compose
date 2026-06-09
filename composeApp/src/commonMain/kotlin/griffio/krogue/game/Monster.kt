package griffio.krogue.game

import kotlin.random.Random

/**
 * The kinds of foe that wander the dungeon. [glyph] is drawn on the map; [hp]
 * and [attack] drive combat; [minDepth] gates how early a kind can appear so the
 * first floors stay gentle. Tuned against the hero's attack power (see
 * [GameState]) so weaker monsters fall in one hit and tougher ones take a few.
 */
enum class MonsterKind(
    val glyph: Char,
    val hp: Int,
    val attack: Int,
    val displayName: String,
    val minDepth: Int,
) {
    RAT('r', hp = 3, attack = 1, displayName = "rat", minDepth = 1),
    KOBOLD('k', hp = 5, attack = 2, displayName = "kobold", minDepth = 1),
    SNAKE('s', hp = 4, attack = 2, displayName = "snake", minDepth = 2),
    ORC('o', hp = 9, attack = 3, displayName = "orc", minDepth = 3),
}

/** One live monster on the current level. Position and hp are mutable. */
class Monster(
    var x: Int,
    var y: Int,
    var hp: Int,
    val kind: MonsterKind,
)

/**
 * Place monsters on distinct, walkable floor tiles inside rooms, never on the
 * hero's spawn. Count and roster scale with [depth]; only kinds whose
 * [MonsterKind.minDepth] has been reached are eligible.
 */
fun spawnMonsters(
    dungeon: Dungeon,
    depth: Int,
    random: Random,
    heroX: Int,
    heroY: Int,
): List<Monster> {
    val eligible = MonsterKind.entries.filter { depth >= it.minDepth }
    if (eligible.isEmpty()) return emptyList()

    val floors = buildList {
        for (room in dungeon.rooms) {
            for (y in room.top..room.bottom) {
                for (x in room.left..room.right) {
                    if (dungeon.terrainAt(x, y) == Terrain.FLOOR && !(x == heroX && y == heroY)) {
                        add(x to y)
                    }
                }
            }
        }
    }
    if (floors.isEmpty()) return emptyList()

    val count = (3 + depth).coerceAtMost(12)
    return floors.shuffled(random).take(count).map { (x, y) ->
        val kind = eligible.random(random)
        Monster(x, y, kind.hp, kind)
    }
}
