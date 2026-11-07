package com.jev.drivecoach
import org.junit.Assert.*
import org.junit.Test
class ManualVoiceTestGateTest {
    @Test fun unknownUxCannotStartInitialization() {
        val gate=ManualVoiceTestGate(); assertFalse(gate.beginInitialization(false)); assertFalse(gate.initializationRequested)
    }
    @Test fun initializationDoesNotTriggerUtterance() {
        val gate=ManualVoiceTestGate(); assertTrue(gate.beginInitialization(true)); assertFalse(gate.utteranceRequested)
    }
    @Test fun apiAvailabilityCannotAuthorizeSpeechBeforeManualPreparation() {
        assertFalse(ManualVoiceTestGate().requestUtterance(true,true))
    }
    @Test fun apiUnavailableOrRestrictedCannotRequest() {
        val gate=ManualVoiceTestGate(); gate.beginInitialization(true)
        assertFalse(gate.requestUtterance(false,true)); assertFalse(gate.requestUtterance(true,false)); assertFalse(gate.utteranceRequested)
    }
    @Test fun noAutomaticRetryAndOneManualUtteranceOnly() {
        val gate=ManualVoiceTestGate(); assertTrue(gate.beginInitialization(true)); assertFalse(gate.beginInitialization(true))
        assertTrue(gate.requestUtterance(true,true)); assertFalse(gate.requestUtterance(true,true)); assertTrue(gate.utteranceRequested)
    }
}
