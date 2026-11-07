package com.jev.drivecoach

/** Confined to LocalSpeech's main-thread dispatcher; no Android/SDK dependency. */
internal class SpeechLifecycle {
    enum class Phase { NEW, INITIALIZING, REQUESTABLE, READY, SPEAKING, CANCELLING, FAILED, CLOSED }
    var phase = Phase.NEW; private set
    var initialized = false; private set
    private var engineReady = false
    private val gate = VoiceGate()
    var requested = 0; private set
    var started = 0; private set
    var completed = 0; private set
    var stopped = 0; private set
    var rmsUpdates = 0; private set
    var lastKey = ""; private set
    var lastError = ""; private set
    private var startedCurrent = false
    val ready get() = phase == Phase.READY
    val canRequest get() = phase == Phase.REQUESTABLE || phase == Phase.READY
    val engineReadyObserved get() = engineReady
    val awaitingInitialization get() = phase == Phase.INITIALIZING
    fun begin(): Boolean {
        if (phase != Phase.NEW) return false
        phase = Phase.INITIALIZING; return true
    }
    fun configured(): Boolean {
        if (phase != Phase.INITIALIZING) return false
        initialized = true
        phase = if (engineReady) Phase.READY else Phase.REQUESTABLE
        return true
    }
    fun engineReady(): Boolean {
        if (phase !in listOf(Phase.INITIALIZING, Phase.REQUESTABLE, Phase.READY, Phase.SPEAKING)) return false
        engineReady = true
        if (initialized && phase != Phase.SPEAKING) phase = Phase.READY
        return true
    }
    fun request(key: String, now: Long): Boolean {
        if (!canRequest || key.isBlank() || !gate.accept(key, now)) return false
        requested++; lastKey = key; startedCurrent = false; phase = Phase.SPEAKING
        return true
    }
    fun start(): Boolean {
        if (phase != Phase.SPEAKING || startedCurrent) return false
        startedCurrent = true; started++; return true
    }
    fun rms(): Boolean {
        if (phase != Phase.SPEAKING) return false
        rmsUpdates++; return true
    }
    fun done(): Boolean {
        if (phase != Phase.SPEAKING) return false
        completed++; phase = if(engineReady) Phase.READY else Phase.REQUESTABLE; return true
    }
    fun cancel(): Boolean {
        if (phase != Phase.SPEAKING) return false
        phase = Phase.CANCELLING; return true
    }
    fun stopAcknowledged(): Boolean {
        if (phase != Phase.SPEAKING && phase != Phase.CANCELLING) return false
        stopped++; phase = if(engineReady) Phase.READY else Phase.REQUESTABLE; return true
    }
    fun fail(error: String): Boolean {
        if (phase == Phase.FAILED || phase == Phase.CLOSED) return false
        lastError = error; phase = Phase.FAILED; return true
    }
    fun initializationTimeout(): Boolean = awaitingInitialization && fail("INIT_TIMEOUT")
    fun requestTimeout(key: String): Boolean = phase == Phase.SPEAKING && key == lastKey && fail("REQUEST_TIMEOUT")
    fun cancellationTimeout(): Boolean = phase == Phase.CANCELLING && fail("STOP_TIMEOUT")
    fun close() { phase = Phase.CLOSED }
}
