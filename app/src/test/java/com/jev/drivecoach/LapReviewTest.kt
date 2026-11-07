package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test
class LapReviewTest {
 private val laps=listOf(LapRecord(4,101.0,34.3,32.1,34.6),LapRecord(7,100.4,33.9,32.8,33.7),LapRecord(9,203.6,33.9,32.0,137.7))
 @Test fun seventhLapFindsS2DespiteFasterTotal() {
  val c=LapReview.compare(laps,1,CompareReference.PRIOR_BEST)
  assertEquals(-0.6,c.totalDelta!!,0.001);assertEquals(0.7,c.sectorDeltas[1],0.001)
  assertTrue(LapReview.text(laps,1,CompareReference.PRIOR_BEST).contains("비교 우선 구간 S2"))
 }
 @Test fun firstLapDoesNotUseFutureAsPriorBest() {
  assertNull(LapReview.compare(laps,0,CompareReference.PRIOR_BEST).reference)
  assertEquals(7,LapReview.compare(laps,0,CompareReference.SESSION_BEST).reference!!.number)
  assertTrue(LapReview.text(laps,0,CompareReference.SESSION_BEST).contains("사후 비교"))
 }
 @Test fun sessionBestComparesToSelfWithoutInventedImprovement() {
  val c=LapReview.compare(laps,1,CompareReference.SESSION_BEST)
  assertEquals(0.0,c.totalDelta!!,0.001)
  assertTrue(LapReview.text(laps,1,CompareReference.SESSION_BEST).contains("현재 랩이 비교 기준"))
 }
 @Test fun anomalousLastLapRetainedButCausalCoachingWithheld() {
  val text=LapReview.text(laps,2,CompareReference.SESSION_BEST)
  assertTrue(text.contains("+104.0"));assertTrue(text.contains("원인 미확정"));assertFalse(text.contains("비교 우선 구간 S3"))
 }
 @Test fun invalidAndInconsistentRecordsDoNotProduceNumericalAdvice() {
  for(bad in listOf(LapRecord(3,Double.NaN,1.0,1.0,1.0),LapRecord(3,3.0,-1.0,2.0,2.0),LapRecord(3,3.0,3.0,3.0,3.0))) {
   val c=LapReview.compare(laps+bad,3,CompareReference.SESSION_BEST)
   assertFalse(c.valid);assertNull(c.totalDelta);assertTrue(c.sectorDeltas.isEmpty())
   assertEquals(7,c.reference!!.number)
  }
 }
}
