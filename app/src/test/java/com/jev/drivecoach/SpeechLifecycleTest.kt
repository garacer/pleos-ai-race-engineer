package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test

class SpeechLifecycleTest {
    private fun prepared() = SpeechLifecycle().apply { begin(); configured(); engineReady() }
    @Test fun configuredApiCanRequestWithoutClaimingEngineReady() {
        val s=SpeechLifecycle();s.begin();s.configured()
        assertTrue(s.initialized);assertTrue(s.canRequest);assertFalse(s.ready);assertFalse(s.engineReadyObserved)
        assertTrue(s.request("lap1",1000));assertFalse(s.canRequest)
        assertTrue(s.engineReady());assertTrue(s.engineReadyObserved)
        assertEquals(SpeechLifecycle.Phase.SPEAKING,s.phase)
        assertFalse(s.request("lap2",5000));assertEquals(1,s.requested)
    }
    @Test fun synchronousReadyCallbackStillWaitsForLocaleConfiguration() {
        val s = SpeechLifecycle(); s.begin(); s.engineReady()
        assertFalse(s.ready); assertFalse(s.request("lap1", 1000))
        s.configured(); assertTrue(s.ready)
    }
    @Test fun timeoutCannotBeUndoneByLateInitializationOrReady() {
        val s = SpeechLifecycle(); s.begin(); assertTrue(s.initializationTimeout())
        assertFalse(s.configured()); assertFalse(s.engineReady()); assertFalse(s.begin())
        assertEquals("INIT_TIMEOUT", s.lastError); assertFalse(s.request("lap1", 1000))
    }
    @Test fun configuredApiDoesNotInventAReadinessTimeout() {
        val s=SpeechLifecycle();s.begin();s.configured()
        assertFalse(s.initializationTimeout());assertEquals("",s.lastError)
        assertTrue(s.canRequest);assertFalse(s.engineReadyObserved)
    }
    @Test fun failedApiCannotRequestEvenAfterLateReadyEvent() {
        val s=SpeechLifecycle();s.begin();s.fail("INIT_FAILURE")
        assertFalse(s.canRequest);assertFalse(s.engineReady());assertFalse(s.request("lap1",1000))
    }
    @Test fun requestFailurePreservesApiAndReadyObservationsWithoutCompletion() {
        val s=SpeechLifecycle();s.begin();s.configured();s.request("probe",1000);s.engineReady();s.start();s.fail("ON_DEVICE_ENGINE_ERROR")
        assertTrue(s.initialized);assertTrue(s.engineReadyObserved);assertFalse(s.canRequest)
        assertEquals(1,s.started);assertEquals(0,s.completed);assertFalse(s.done())
    }
    @Test fun engineErrorIsNotOverwrittenByTimerOrLateCallbacks() {
        val s = prepared(); s.request("lap1", 1000); s.start(); s.fail("ON_DEVICE_ENGINE_ERROR")
        assertFalse(s.initializationTimeout()); assertFalse(s.engineReady()); assertFalse(s.done())
        assertFalse(s.request("lap2", 8000)); assertEquals(0, s.completed)
        assertEquals("ON_DEVICE_ENGINE_ERROR", s.lastError)
    }
    @Test fun oneActiveRequestAndOneCompletionWithNoQueue() {
        val s = prepared(); assertTrue(s.request("lap1", 1000))
        assertFalse(s.request("lap2", 5000)); assertTrue(s.start()); assertFalse(s.start())
        assertTrue(s.done()); assertFalse(s.done()); assertEquals(1, s.completed)
        assertTrue(s.request("lap2", 5000)); assertEquals(2, s.requested)
    }
    @Test fun cancellationDoesNotCountLateDoneAsSuccessfulPlayback() {
        val s = prepared(); s.request("lap1", 1000); assertTrue(s.cancel())
        assertFalse(s.done()); assertFalse(s.request("lap2", 5000))
        assertTrue(s.stopAcknowledged()); assertEquals(0, s.completed)
        assertTrue(s.request("lap2", 5000))
    }
    @Test fun missingStopAcknowledgementFailsClosed() {
        val s = prepared(); s.request("lap1", 1000); s.cancel()
        assertTrue(s.cancellationTimeout()); assertFalse(s.stopAcknowledged())
        assertEquals("STOP_TIMEOUT", s.lastError); assertFalse(s.ready)
    }
    @Test fun oldRequestDeadlineDoesNotFailANewerRequest() {
        val s = prepared(); s.request("lap1", 1000); s.done(); s.request("lap2", 5000)
        assertFalse(s.requestTimeout("lap1")); assertTrue(s.requestTimeout("lap2"))
        assertFalse(s.done()); assertEquals(1, s.completed)
    }
    @Test fun duplicateAndTooRapidKeysRemainSuppressed() {
        val s = prepared(); s.request("lap1", 1000); s.done()
        assertFalse(s.request("lap1", 6000)); assertFalse(s.request("lap2", 2000))
        assertTrue(s.request("lap2", 4000))
    }
    @Test fun closedSessionRejectsLateCallbacksAndNewRequests() {
        val s = prepared(); s.request("lap1", 1000); s.close()
        assertFalse(s.configured()); assertFalse(s.engineReady()); assertFalse(s.start())
        assertFalse(s.done()); assertFalse(s.stopAcknowledged()); assertFalse(s.fail("LATE"))
        assertFalse(s.request("lap2", 8000)); assertEquals(0, s.completed)
        assertEquals(SpeechLifecycle.Phase.CLOSED, s.phase)
    }
}
