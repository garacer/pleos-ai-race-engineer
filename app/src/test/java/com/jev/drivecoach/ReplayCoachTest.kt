package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test

class ReplayCoachTest {
    private val laps=listOf(
        LapRecord(1,144.1,45.3,50.3,48.5),LapRecord(2,104.0,36.0,33.7,34.3),
        LapRecord(3,101.5,34.4,32.3,34.8),LapRecord(4,101.0,34.3,32.1,34.6),
        LapRecord(5,102.8,34.5,32.8,35.5),LapRecord(6,104.6,35.7,33.0,35.9),
        LapRecord(7,100.4,33.9,32.8,33.7),LapRecord(8,101.8,34.9,32.4,34.5),
        LapRecord(9,203.6,33.9,32.0,137.7))
    @Test fun seventhLapMatchesKnownBestAndSectors() {
        val c=ReplayCoach.describe(laps,6)
        assertTrue(c.shortText.contains("1:40.4"))
        assertTrue(c.shortText.contains("0.6초"))
        assertTrue(c.detail.contains("S1 -0.4 / S2 +0.7 / S3 -0.9"))
        assertTrue(c.voiceText.contains("1분 40.4초"))
    }
    @Test fun allNineAreReplayAndLastCauseUnknown() {
        for(i in laps.indices) assertTrue(ReplayCoach.describe(laps,i).shortText.startsWith("기록 재생."))
        assertTrue(ReplayCoach.describe(laps,8).detail.contains("원인 미확인"))
        assertTrue(ReplayCoach.describe(laps,8).detail.contains("137.7"))
    }
    @Test fun pauseStopRestartAndPreviousNext() {
        val s=ReplaySession(9);s.play();s.tick();assertEquals(1,s.index)
        s.pause();s.tick();assertEquals(1,s.index)
        s.stop();s.play();assertEquals(0,s.index)
        val run=s.runId;s.next();s.restart();assertEquals(0,s.index);assertTrue(s.runId>run)
        s.previous();assertEquals(8,s.index);s.next();assertEquals(0,s.index)
    }
    @Test fun automaticReplayEndsInsteadOfLooping() {
        val s=ReplaySession(9);s.play();repeat(9){s.tick()}
        assertEquals(8,s.index);assertEquals("COMPLETE",s.state)
    }
    @Test fun voiceDuplicatesAndRapidQueueAreSuppressed() {
        val g=VoiceGate();assertTrue(g.accept("1-1",1000))
        assertFalse(g.accept("1-1",9000));assertFalse(g.accept("1-2",2000))
        assertTrue(g.accept("1-2",4000));assertTrue(g.accept("2-1",7000))
    }
    @Test fun onlyLocalKoreanVoiceAndConfirmedUnrestrictedUi() {
        assertTrue(offlineKorean("ko",false));assertFalse(offlineKorean("ko",true));assertFalse(offlineKorean("en",false))
        assertTrue(detailsAllowed(false,0));assertFalse(detailsAllowed(null,null))
        assertFalse(detailsAllowed(true,0));assertFalse(detailsAllowed(false,8))
    }
}
