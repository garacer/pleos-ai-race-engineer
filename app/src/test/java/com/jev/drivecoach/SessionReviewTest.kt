package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test
class SessionReviewTest {
 private val laps=listOf(LapRecord(1,144.1,45.3,50.3,48.5),LapRecord(2,104.0,36.0,33.7,34.3),LapRecord(3,101.5,34.4,32.3,34.8),LapRecord(4,101.0,34.3,32.1,34.6),LapRecord(5,102.8,34.5,32.8,35.5),LapRecord(6,104.6,35.7,33.0,35.9),LapRecord(7,100.4,33.9,32.8,33.7),LapRecord(8,101.8,34.9,32.4,34.5),LapRecord(9,203.6,33.9,32.0,137.7))
 @Test fun allNineIncludingLongLastLapRemainInStats() {
  val s=SessionReview.stats(laps)
  assertEquals(9,s.count);assertEquals(7,s.best!!.number)
  assertEquals(1063.8,s.total!!,0.001);assertEquals(118.2,s.mean!!,0.001);assertEquals(102.8,s.median!!,0.001)
 }
 @Test fun summaryCarriesChosenReferenceAndCaution() {
  val prior=SessionReview.text(laps,CompareReference.PRIOR_BEST)
  val whole=SessionReview.text(laps,CompareReference.SESSION_BEST)
  assertTrue(prior.contains("각 랩 이전"))
  assertTrue(whole.contains("전체 기록 사후 비교"));assertTrue(whole.contains("9랩 3:23.6"))
  assertTrue(whole.contains("원인 미확정"));assertTrue(whole.contains("현재 랩 음성이 아님"))
  assertTrue(prior.contains("1랩 2:24.1 | 기준 없음"));assertTrue(whole.contains("1랩 2:24.1 | 7랩 대비 +43.7초"))
 }
 @Test fun invalidRecordWithholdsAggregateInsteadOfSilentlyDeletingIt() {
  val bad=laps+LapRecord(10,Double.NaN,1.0,1.0,1.0)
  val s=SessionReview.stats(bad);assertEquals(10,s.count);assertEquals(1,s.invalidCount);assertNull(s.total);assertNull(s.best)
  assertTrue(SessionReview.text(bad,CompareReference.SESSION_BEST).contains("집계·순위 계산 보류"))
 }
 @Test fun emptyAndEvenSizedSessionsAreDefined() {
  assertNull(SessionReview.stats(emptyList()).best)
  assertTrue(SessionReview.text(emptyList(),CompareReference.PRIOR_BEST).contains("기록 없음"))
  assertEquals(124.05,SessionReview.stats(laps.take(2)).median!!,0.001)
 }
}
