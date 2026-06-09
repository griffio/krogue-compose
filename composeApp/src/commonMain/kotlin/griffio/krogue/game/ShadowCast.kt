package griffio.krogue.game

import kotlin.math.ceil

/**
 * Recursive shadowcasting field of view.
 *
 * Sweeps the eight octants around the origin, marking each visible cell via
 * [setVisible]. A cell is opaque (and therefore casts a shadow) when
 * [isOpaque] returns true. Ported from the improved RogueBasin algorithm used
 * in `krogue-kotter`, generalised to operate on any grid through callbacks.
 *
 * See: http://www.roguebasin.com/index.php?title=FOV_using_recursive_shadowcasting_-_improved
 */
object ShadowCast {

    private data class Octant(val xx: Int, val xy: Int, val yx: Int, val yy: Int)

    private val octants = listOf(
        Octant(0, -1, 1, 0),
        Octant(1, 0, 0, -1),
        Octant(1, 0, 0, 1),
        Octant(0, 1, 1, 0),
        Octant(0, 1, -1, 0),
        Octant(-1, 0, 0, 1),
        Octant(-1, 0, 0, -1),
        Octant(0, -1, -1, 0),
    )

    fun compute(
        originX: Int,
        originY: Int,
        radius: Int,
        width: Int,
        height: Int,
        isOpaque: (Int, Int) -> Boolean,
        setVisible: (Int, Int) -> Unit,
    ) {
        // The origin is always visible.
        setVisible(originX, originY)
        for (octant in octants) {
            castLight(
                originX, originY, radius.toDouble(), width, height,
                startColumn = 1, leftSlope = 1.0, rightSlope = 0.0,
                octant = octant, isOpaque = isOpaque, setVisible = setVisible,
            )
        }
    }

    private fun castLight(
        originX: Int,
        originY: Int,
        viewRadius: Double,
        width: Int,
        height: Int,
        startColumn: Int,
        leftSlope: Double,
        rightSlope: Double,
        octant: Octant,
        isOpaque: (Int, Int) -> Boolean,
        setVisible: (Int, Int) -> Unit,
    ) {
        var leftViewSlope = leftSlope
        val viewRadiusSq = viewRadius * viewRadius
        val viewCeiling = ceil(viewRadius).toInt()

        var prevWasBlocked = false
        var savedRightSlope = -1.0

        var currentCol = startColumn
        while (currentCol <= viewCeiling) {
            var yc = currentCol
            while (yc >= 0) {
                val gridX = originX + currentCol * octant.xx + yc * octant.xy
                val gridY = originY + currentCol * octant.yx + yc * octant.yy

                if (gridX < 0 || gridX >= width || gridY < 0 || gridY >= height) {
                    yc--
                    continue
                }

                val leftBlockSlope = (yc + 0.5) / (currentCol - 0.5)
                val rightBlockSlope = (yc - 0.5) / (currentCol + 0.5)

                if (rightBlockSlope > leftViewSlope) {
                    yc--
                    continue
                } else if (leftBlockSlope < rightSlope) {
                    break
                }

                val distanceSq = (currentCol * currentCol + yc * yc).toDouble()
                if (distanceSq <= viewRadiusSq) {
                    setVisible(gridX, gridY)
                }

                val curBlocked = isOpaque(gridX, gridY)
                if (prevWasBlocked) {
                    if (curBlocked) {
                        savedRightSlope = rightBlockSlope
                    } else {
                        prevWasBlocked = false
                        leftViewSlope = savedRightSlope
                    }
                } else {
                    if (curBlocked) {
                        if (leftBlockSlope <= leftViewSlope) {
                            castLight(
                                originX, originY, viewRadius, width, height,
                                currentCol + 1, leftViewSlope, leftBlockSlope,
                                octant, isOpaque, setVisible,
                            )
                        }
                        prevWasBlocked = true
                        savedRightSlope = rightBlockSlope
                    }
                }
                yc--
            }

            if (prevWasBlocked) break
            currentCol++
        }
    }
}
