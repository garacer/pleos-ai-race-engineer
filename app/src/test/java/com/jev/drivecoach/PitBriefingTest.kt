package com.jev.drivecoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitBriefingTest {
    private val laps = listOf(
        LapRecord(1, 144.1, 45.3, 50.3, 48.5), LapRecord(2, 104.0, 36.0, 33.7, 34.3),
        LapRecord(3, 101.5, 34.4, 32.3, 34.8), LapRecord(4, 101.0, 34.3, 32.1, 34.6),
        LapRecord(5, 102.8, 34.5, 32.8, 35.5), LapRecord(6, 104.6, 35.7, 33.0, 35.9),
        LapRecord(7, 100.4, 33.9, 32.8, 33.7), LapRecord(8, 101.8, 34.9, 32.4, 34.5),
        LapRecord(9, 203.6, 33.9, 32.0, 137.7),
    )
    private val b = PitBriefing.analyze(laps)

    @Test fun outAndInLapsAreDetected() {
        val kinds = PitBriefing.classify(laps)
        assertEquals(LapKind.OUT, kinds.first())
        assertEquals(LapKind.IN, kinds.last())
        assertEquals(7, kinds.count { it == LapKind.FLYING })
    }

    @Test fun midSessionSlowLapIsNotCalledInOrOut() {
        val l = laps.toMutableList(); l[4] = LapRecord(5, 130.0, 40.0, 50.0, 40.0)
        assertEquals(LapKind.SLOW, PitBriefing.classify(l)[4])
    }

    @Test fun bestLapIgnoresOutAndInLaps() = assertEquals(7, b.best.number)

    @Test fun inLapPitSectorIsExcludedButItsCleanSectorsCount() {
        assertEquals(listOf(7, 9, 7), b.bestSectors.map { it.lap }) // S1 tie keeps first (lap 7)
        assertEquals(32.0, b.bestSectors[1].seconds, 1e-9)
        assertNull(b.rows.last().sectorLoss[2])
        assertEquals(99.6, b.theoretical, 1e-9)
    }

    @Test fun focusIsSectorTwo() {
        assertEquals(1, b.focusSector)
        assertEquals(0.8, b.focusLoss, 1e-9)
    }

    @Test fun consistencyUsesFlyingLapsOnly() {
        assertEquals(7, b.flyingCount)
        assertEquals(102.3, b.flyingMean, 0.01)
        assertTrue(b.flyingStdDev in 1.3..1.6)
        assertEquals(2, b.withinOnePercent) // 7 (100.4) and 4 (101.0); 101.5 > 101.404
    }

    @Test fun biggestLossBetweenCorners() {
        val c = listOf(
            CornerSnapshot(1, 57.6, 55.1, -0.1), CornerSnapshot(2, 63.7, 53.8, -0.3),
            CornerSnapshot(3, 66.4, null, null), CornerSnapshot(7, 65.0, 61.3, 0.2),
            CornerSnapshot(8, 107.0, 94.6, 0.1), CornerSnapshot(10, 72.9, 67.9, -0.4),
        )
        val loss = PitBriefing.biggestLoss(c)!!
        assertEquals(2, loss.fromTurn); assertEquals(7, loss.toTurn)
        assertEquals(0.5, loss.lost, 1e-9)
        assertEquals(5 to 5, PitBriefing.fasterApexCount(c))
    }

    @Test fun noLossWhenGapOnlyShrinks() {
        assertNull(PitBriefing.biggestLoss(listOf(CornerSnapshot(1, null, null, 0.3), CornerSnapshot(2, null, null, 0.1))))
    }

    @Test fun spokenBriefingMentionsKeyNumbers() {
        val s = PitBriefing.spoken(b, TimeLossSegment(2, 7, 0.5))
        assertTrue(s, s.contains("7랩 1분 40.4초"))
        assertTrue(s, s.contains("1분 39.6초"))
        assertTrue(s, s.contains("섹터 2, 0.8초"))
        assertTrue(s, s.contains("턴 2에서 턴 7"))
    }
}

