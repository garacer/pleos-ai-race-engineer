package com.jev.drivecoach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebriefTest {
    private val laps = listOf(
        LapRecord(1, 144.1, 45.3, 50.3, 48.5), LapRecord(2, 104.0, 36.0, 33.7, 34.3),
        LapRecord(3, 101.5, 34.4, 32.3, 34.8), LapRecord(4, 101.0, 34.3, 32.1, 34.6),
        LapRecord(5, 102.8, 34.5, 32.8, 35.5), LapRecord(6, 104.6, 35.7, 33.0, 35.9),
        LapRecord(7, 100.4, 33.9, 32.8, 33.7), LapRecord(8, 101.8, 34.9, 32.4, 34.5),
        LapRecord(9, 203.6, 33.9, 32.0, 137.7),
    )
    private val corners = listOf(
        CornerSnapshot(1, 57.6, 55.1, -0.1), CornerSnapshot(2, 63.7, 53.8, -0.3),
        CornerSnapshot(3, 66.4, null, null), CornerSnapshot(7, 65.0, 61.3, 0.2),
        CornerSnapshot(8, 107.0, 94.6, 0.1), CornerSnapshot(10, 72.9, 67.9, -0.4),
    )
    private val b = PitBriefing.analyze(laps)
    private val cards = Debrief.cards(b, PitBriefing.biggestLoss(corners), corners)

    @Test fun fiveMessagesInRadioOrder() =
        assertEquals(listOf("session", "potential", "loss", "good", "mission"), cards.map { it.id })

    @Test fun lapTimeIsReadTheRadioWay() {
        assertEquals("1분 40초 4", Debrief.say(100.4))
        assertEquals("1분 39초 6", Debrief.say(99.6))
        assertEquals("3분 23초 6", Debrief.say(203.6))
    }

    @Test fun missionUsesFlyingLapNotInLap() {
        val t = Debrief.missionTarget(b, 2)
        assertEquals(4, t.lap); assertEquals(32.1, t.seconds, 1e-9)
        assertEquals("섹터 2, 4랩처럼", cards.last().headline)
        assertTrue(cards.last().spoken.contains("32.1초"))
    }

    @Test fun headlinesStayShort() = cards.forEach { assertTrue(it.headline, it.headline.length <= 14) }

    @Test fun potentialAndLoss() {
        assertEquals("0.8초", cards[1].headline)
        assertEquals("턴 2 → 턴 7", cards[2].headline)
        assertTrue(cards[3].sub.contains("턴 8, +12 km/h"))
    }

    @Test fun lossCardDroppedWhenNoCornerData() {
        val c = Debrief.cards(b, null, emptyList())
        assertEquals(listOf("session", "potential", "mission"), c.map { it.id })
    }

    @Test fun payloadForPreview() {
        val json = DebriefJson.build(b, PitBriefing.biggestLoss(corners), cards)
        assertTrue(json.contains("\"cards\":[{\"id\":\"session\""))
        java.io.File("build/debrief-preview.json").writeText(json)
    }
}
