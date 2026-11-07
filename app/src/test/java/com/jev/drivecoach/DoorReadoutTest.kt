package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test

class DoorReadoutTest {
    @Test fun freshThenStale() {
        val r = DoorReadout("LIVE", 0, 1000, "utc")
        assertEquals("LIVE", r.freshness(7000))
        assertEquals("STALE", r.freshness(7001))
        assertTrue(r.display(7001).contains("현재값 미확인"))
    }
    @Test fun unavailableIsNotZero() {
        assertEquals("UNAVAILABLE", DoorReadout("LIVE").freshness(10))
        assertFalse(DoorReadout("UNAVAILABLE").display(10).contains("문 위치: 0"))
    }
    @Test fun unsupportedAndFailureRemainDistinct() {
        assertEquals("UNSUPPORTED", DoorReadout("UNSUPPORTED").freshness(0))
        assertEquals("ERROR", DoorReadout("ERROR").freshness(0))
    }
    @Test fun disconnectedDoesNotPresentOldValueAsCurrent() {
        val r = DoorReadout("DISCONNECTED", 1, 0, "utc")
        assertEquals("DISCONNECTED", r.freshness(1))
        assertTrue(r.display(1).contains("마지막 수신값 1"))
    }
    @Test fun capabilityRequiresReadableArea() {
        assertTrue(supportsDoorRead("READ", true))
        assertTrue(supportsDoorRead("READ_WRITE", true))
        assertFalse(supportsDoorRead("WRITE", true))
        assertFalse(supportsDoorRead("NONE", true))
        assertFalse(supportsDoorRead("READ", false))
    }
}
