package com.jev.drivecoach

/** Manual, at-most-once voice check. API requestability and engine/playback success are separate observations. */
internal class ManualVoiceTestGate {
    var initializationRequested = false; private set
    var utteranceRequested = false; private set
    fun beginInitialization(detailsAllowed: Boolean): Boolean {
        if (!detailsAllowed || initializationRequested) return false
        initializationRequested = true; return true
    }
    fun requestUtterance(apiRequestable: Boolean, detailsAllowed: Boolean): Boolean {
        if (!initializationRequested || !apiRequestable || !detailsAllowed || utteranceRequested) return false
        utteranceRequested = true; return true
    }
}
