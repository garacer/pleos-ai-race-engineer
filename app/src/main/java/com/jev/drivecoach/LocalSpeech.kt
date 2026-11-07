package com.jev.drivecoach

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.util.Locale

/** ON_DEVICE only. Initialized API != engine ready != successful speech. */
class LocalSpeech(private val context: Context, private val changed: (String) -> Unit) {
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private val state = SpeechLifecycle()
    private var requestDeadline: Runnable? = null
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) action() else handler.post { action() }
    }
    private fun event(action: () -> Unit) = onMain {
        if (state.phase != SpeechLifecycle.Phase.CLOSED) action()
    }
    private fun record(event: String) {
        val value = JSONObject().put("mode", "ON_DEVICE").put("locale", "ko-KR")
            .put("phase", state.phase.name).put("initialized", state.initialized).put("ready", state.ready)
            .put("can_request",state.canRequest).put("engine_ready_observed",state.engineReadyObserved)
            .put("requested", state.requested).put("started", state.started).put("completed", state.completed)
            .put("stopped", state.stopped).put("rms_updates", state.rmsUpdates).put("last_key", state.lastKey)
            .put("last_error", state.lastError).put("event", event).put("utc_ms", System.currentTimeMillis())
            .put("registration_called", false)
        try { File(context.filesDir, "pleos-local-tts.json").writeText(value.toString(2)) }
        catch (e: Exception) { Log.w("JEV_TTS", "SNAPSHOT_WRITE_FAILED ${e.javaClass.simpleName}") }
        Log.i("JEV_TTS", value.toString())
    }
    private fun clearRequestDeadline() {
        requestDeadline?.let { handler.removeCallbacks(it) }; requestDeadline = null
    }
    private fun unavailable(event: String) {
        handler.removeCallbacks(initializationDeadline); clearRequestDeadline(); handler.removeCallbacks(cancelDeadline)
        record(event)
        changed("동적 음성 사용 불가: ${state.lastError} · 자막 계속 · 자동 재시도 없음")
    }
    private val initializationDeadline = Runnable {
        if (state.initializationTimeout()) unavailable("INITIALIZATION_TIMEOUT")
    }
    private val cancelDeadline = Runnable {
        if (state.cancellationTimeout()) unavailable("STOP_TIMEOUT")
    }
    private val listener = object: EventListener {
        override fun onReady() = event {
            if (state.engineReady()) {
                if (state.initialized) handler.removeCallbacks(initializationDeadline)
                record("ENGINE_READY")
                changed("음성 요청 가능 · 완료 ${state.completed}/${state.requested} · 중지 ${state.stopped} · 실제 청취 별도")
            }
        }
        override fun onStart() = event { if (state.start()) record("START") }
        override fun onUpdatedRms(rms: Double) = event { state.rms() }
        override fun onDone() = event {
            if (state.done()) {
                clearRequestDeadline(); record("DONE")
                changed("동적 한국어 완료 콜백 ${state.completed}/${state.requested} · 실제 청취 별도 확인")
            }
        }
        override fun onStop() = event {
            if (state.stopAcknowledged()) {
                clearRequestDeadline(); handler.removeCallbacks(cancelDeadline); record("STOP")
                changed("음성 중지 확인 · 완료 ${state.completed}/${state.requested} · 중지 ${state.stopped} · 실제 청취 별도")
            }
        }
        override fun onError(errorType: ErrorType) = event {
            if (state.fail(errorType.toString())) unavailable("ERROR")
        }
    }
    fun initialize() = onMain {
        if (!state.begin()) return@onMain
        record("INITIALIZING"); handler.postDelayed(initializationDeadline, 8000)
        try {
            tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
            tts!!.initialize(object: OnInitializeListener {
                override fun onSuccess() = event {
                    if (state.phase != SpeechLifecycle.Phase.INITIALIZING) return@event
                    try {
                        if (state.phase != SpeechLifecycle.Phase.INITIALIZING) return@event
                        tts?.setLocale(Locale.KOREA)
                        if (state.configured()) {
                            handler.removeCallbacks(initializationDeadline)
                            record("API_INITIALIZED")
                            changed("API 초기화·한국어 설정 완료 · 수동 요청 가능 · 엔진/발화 성공 미검증")
                        }
                    } catch (e: Exception) { if (state.fail(e.javaClass.simpleName)) unavailable("INIT_EXCEPTION") }
                }
                override fun onFailure(errorType: ErrorType) = event {
                    if (state.fail(errorType.toString())) unavailable("INIT_FAILURE")
                }
            })
            // initialize() creates/binds the SDK backend. Subscribe immediately after
            // invoking it, rather than waiting for onSuccess: the service can emit
            // readiness during connection before that completion callback.
            tts!!.addEventListener(listener)
            record("LISTENER_REGISTERED")
        } catch (e: Exception) { if (state.fail(e.javaClass.simpleName)) unavailable("INIT_EXCEPTION") }
    }
    fun speak(key: String, text: String) = onMain {
        if (text.isBlank() || !state.request(key, SystemClock.elapsedRealtime())) return@onMain
        record("REQUEST")
        val deadline = Runnable {
            if (state.requestTimeout(key)) {
                unavailable("REQUEST_TIMEOUT")
                try { tts?.stop() } catch (_: Exception) {}
            }
        }
        requestDeadline = deadline; handler.postDelayed(deadline, 14000)
        try { tts!!.speak(text) }
        catch (e: Exception) { if (state.fail(e.javaClass.simpleName)) unavailable("REQUEST_EXCEPTION") }
    }
    fun canRequestForTest(): Boolean = state.canRequest
    fun stop() = onMain {
        if (!state.cancel()) return@onMain
        clearRequestDeadline(); record("CANCELLING"); handler.postDelayed(cancelDeadline, 3000)
        try { tts?.stop() }
        catch (e: Exception) { if (state.fail(e.javaClass.simpleName)) unavailable("STOP_EXCEPTION") }
    }
    fun close() = onMain {
        if (state.phase == SpeechLifecycle.Phase.CLOSED) return@onMain
        state.close(); handler.removeCallbacksAndMessages(null)
        try { tts?.removeEventListener(listener) } catch (_: Exception) {}
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.release() } catch (_: Exception) {}
        tts = null; record("CLOSED")
    }
}
