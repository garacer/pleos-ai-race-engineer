package com.jev.drivecoach

import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Pit-wall analysis of recorded lap/sector times. Pure Kotlin, no Android types.
 *
 * Rules (all deterministic, documented in README):
 *  - A lap slower than [SLOW_LAP_RATIO] x median lap is not a flying lap.
 *    First such lap = OUT, last such lap = IN, others = SLOW.
 *  - A sector slower than [SLOW_SECTOR_RATIO] x that sector's median is excluded from
 *    "best sector" (so the in-lap's pit-entry S3 is dropped but its S1/S2 still count).
 *  - Theoretical best = sum of best valid sectors.
 */
enum class LapKind { FLYING, OUT, IN, SLOW }

data class SectorBest(val sector: Int, val seconds: Double, val lap: Int)

data class LapRow(
    val lap: LapRecord,
    val kind: LapKind,
    val deltaToBest: Double,
    /** per sector: seconds slower than session best sector, null if sector excluded */
    val sectorLoss: List<Double?>,
    /** per sector: this lap set the session best sector */
    val sectorIsBest: List<Boolean>,
)

data class CornerSnapshot(
    val turn: Int,
    val subjectSpeed: Double?,
    val referenceSpeed: Double?,
    /** cumulative time difference subject - reference at this point, seconds */
    val elapsedDelta: Double?,
)

data class TimeLossSegment(val fromTurn: Int, val toTurn: Int, val lost: Double)

data class Briefing(
    val rows: List<LapRow>,
    val best: LapRecord,
    val bestSectors: List<SectorBest>,
    val theoretical: Double,
    val flyingCount: Int,
    val flyingMean: Double,
    val flyingStdDev: Double,
    val withinOnePercent: Int,
    /** sector index (0-based) where the best lap lost most vs best sectors */
    val focusSector: Int,
    val focusLoss: Double,
)

object PitBriefing {
    const val SLOW_LAP_RATIO = 1.10
    const val SLOW_SECTOR_RATIO = 1.15

    fun median(xs: List<Double>): Double {
        require(xs.isNotEmpty())
        val s = xs.sorted(); val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2
    }

    private fun sectors(l: LapRecord) = listOf(l.s1, l.s2, l.s3)

    fun classify(laps: List<LapRecord>): List<LapKind> {
        val med = median(laps.map { it.seconds })
        val slow = laps.map { it.seconds > med * SLOW_LAP_RATIO }
        return laps.indices.map { i ->
            when {
                !slow[i] -> LapKind.FLYING
                i == 0 -> LapKind.OUT
                i == laps.lastIndex -> LapKind.IN
                else -> LapKind.SLOW
            }
        }
    }

    fun analyze(laps: List<LapRecord>): Briefing {
        require(laps.isNotEmpty())
        val kinds = classify(laps)
        val sectorMedians = (0..2).map { k -> median(laps.map { sectors(it)[k] }) }
        val validSector = laps.map { l -> (0..2).map { k -> sectors(l)[k] <= sectorMedians[k] * SLOW_SECTOR_RATIO } }
        val bestSectors = (0..2).map { k ->
            val i = laps.indices.filter { validSector[it][k] }.minBy { sectors(laps[it])[k] }
            SectorBest(k + 1, sectors(laps[i])[k], laps[i].number)
        }
        val flying = laps.filterIndexed { i, _ -> kinds[i] == LapKind.FLYING }.ifEmpty { laps }
        val best = flying.minBy { it.seconds }
        val rows = laps.mapIndexed { i, l ->
            LapRow(
                l, kinds[i], l.seconds - best.seconds,
                (0..2).map { k -> if (validSector[i][k]) sectors(l)[k] - bestSectors[k].seconds else null },
                (0..2).map { k -> bestSectors[k].lap == l.number },
            )
        }
        val times = flying.map { it.seconds }
        val mean = times.average()
        val sd = sqrt(times.sumOf { (it - mean) * (it - mean) } / times.size)
        val bestLoss = (0..2).map { k -> sectors(best)[k] - bestSectors[k].seconds }
        val focus = bestLoss.indices.maxBy { bestLoss[it] }
        return Briefing(
            rows, best, bestSectors, bestSectors.sumOf { it.seconds },
            flying.size, mean, sd, flying.count { it.seconds <= best.seconds * 1.01 },
            focus, bestLoss[focus],
        )
    }

    /**
     * Largest growth of the cumulative gap between two consecutive compared corners.
     * Only corners with a reference value are used; returns null if fewer than two.
     */
    fun biggestLoss(corners: List<CornerSnapshot>): TimeLossSegment? {
        val c = corners.filter { it.elapsedDelta != null }.sortedBy { it.turn }
        if (c.size < 2) return null
        return c.zipWithNext { a, b -> TimeLossSegment(a.turn, b.turn, b.elapsedDelta!! - a.elapsedDelta!!) }
            .maxBy { it.lost }.takeIf { it.lost > 0.05 }
    }

    fun fasterApexCount(corners: List<CornerSnapshot>): Pair<Int, Int> {
        val c = corners.filter { it.subjectSpeed != null && it.referenceSpeed != null }
        return c.count { it.subjectSpeed!! > it.referenceSpeed!! } to c.size
    }

    private fun f1(v: Double) = String.format(Locale.US, "%.1f", abs(v))

    /** Korean spoken briefing, ~15 s. Numbers spoken as minutes/seconds. */
    fun spoken(b: Briefing, loss: TimeLossSegment?): String {
        fun say(s: Double) = "${(s / 60).toInt()}분 ${f1(s % 60)}초"
        val gap = b.best.seconds - b.theoretical
        val where = loss?.let { " 특히 턴 ${it.fromTurn}에서 턴 ${it.toTurn} 사이에서 ${f1(it.lost)}초를 잃었습니다." } ?: ""
        return "베스트는 ${b.best.number}랩 ${say(b.best.seconds)}입니다. " +
            "섹터 베스트를 이으면 ${say(b.theoretical)}, ${f1(gap)}초 여유가 있습니다. " +
            "가장 큰 기회는 섹터 ${b.focusSector + 1}, ${f1(b.focusLoss)}초입니다.$where"
    }
}
