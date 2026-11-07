package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test
class CircuitDataTest {
 @Test fun missingGpsNeverBecomesLive(){assertEquals("DISCONNECTED",CircuitData.status(null,10000))}
 @Test fun coordinateRangeAndNonFiniteValuesAreRejected(){
  for(f in listOf(GpsFix(Double.NaN,127.0),GpsFix(91.0,0.0),GpsFix(0.0,181.0)))assertEquals("INVALID",CircuitData.status(f,10000))
 }
 @Test fun unknownFutureAndStaleTimesStayDistinct(){
  assertEquals("TIME_UNKNOWN",CircuitData.status(GpsFix(37.0,127.0),10000))
  assertEquals("INVALID_TIME",CircuitData.status(GpsFix(37.0,127.0,receivedElapsedMs=10001),10000))
  assertEquals("STALE",CircuitData.status(GpsFix(37.0,127.0,receivedElapsedMs=1),10000))
  assertEquals("LIVE",CircuitData.status(GpsFix(37.0,127.0,receivedElapsedMs=9990),10000))
 }
 @Test fun replayIsNotPresentedAsLive(){assertEquals("REPLAY",CircuitData.status(GpsFix(37.0,127.0,source=PositionSource.REPLAY),10000))}
 @Test fun directionIsNeverInferredFromGeometry(){
  assertNull(CircuitData.direction(GpsFix(37.0,127.0)))
  assertNull(CircuitData.direction(GpsFix(37.0,127.0,360.0)))
  assertEquals(90.0,CircuitData.direction(GpsFix(37.0,127.0,90.0))!!,0.001)
 }
 @Test fun projectionFitsAndPreservesAspectIncludingSinglePoint(){
  assertEquals(MapPoint(0.5,0.5),CircuitData.project(listOf(GpsFix(37.0,127.0))).single())
  val p=CircuitData.project(listOf(GpsFix(37.0,127.0),GpsFix(37.01,127.01)))
  assertTrue(p.all {it.x in 0.09..0.91 && it.y in 0.09..0.91})
  assertTrue(p[1].x>p[0].x);assertTrue(p[1].y<p[0].y)
 }
 @Test fun datelineTraceDoesNotWrapAroundTheWholeWorld(){
  val p=CircuitData.project(listOf(GpsFix(0.0,179.99),GpsFix(0.0,-179.99)))
  assertTrue(p[1].x>p[0].x)
 }
}
