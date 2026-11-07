package com.jev.drivecoach

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Only capability/get calls. No SDK control/set API or AAOS substitute. */
class ReadOnlyDoorProvider(private val context: Context, private val changed: (DoorReadout) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val area = DoorArea(DoorValue.ROW_1_LEFT)
    private var vehicle: Vehicle? = null
    private var generation = 0
    private var active = false
    private var supported = false
    private var pending = false
    private var pendingAt = 0L
    private var lastQuery = 0L
    var readout = DoorReadout()
        private set

    private fun publish(next: DoorReadout) { readout = next; changed(next) }
    private fun onSession(id: Int, action: () -> Unit) {
        handler.post { if (active && generation == id) { pending = false; action() } }
    }
    fun connect() {
        disconnect()
        active = true
        publish(DoorReadout(detail = "권한 CAR_DOORS · 읽기 전용 · ROW_1_LEFT"))
        try { vehicle = Vehicle(context.applicationContext).also { it.initialize() } }
        catch (e: Exception) { publish(readout.copy(state = "ERROR", detail = e.javaClass.simpleName + ": " + e.message)) }
        handler.post(tick)
    }
    fun disconnect() {
        active = false; generation++; pending = false; supported = false
        handler.removeCallbacksAndMessages(null)
        try { vehicle?.release() } catch (_: Exception) {}
        vehicle = null
        publish(readout.copy(state = "DISCONNECTED", detail = "연결 해제 · 마지막 값은 현재값으로 사용하지 않음"))
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            val now = SystemClock.elapsedRealtime()
            changed(readout)
            if (pending && now - pendingAt > 5000) {
                // Abandon callbacks from the timed-out session before retrying.
                generation++; pending = false; supported = false
                publish(readout.copy(state = "ERROR", detail = "응답 시간 초과 · 서비스 재확인"))
            }
            if (!pending && now - lastQuery >= 2000 && readout.state != "UNSUPPORTED") query()
            handler.postDelayed(this, 1000)
        }
    }
    private fun query() {
        val id = generation
        pending = true; pendingAt = SystemClock.elapsedRealtime(); lastQuery = pendingAt
        try {
            if (vehicle == null) vehicle = Vehicle(context.applicationContext).also { it.initialize() }
            val door = vehicle!!.getDoor()
            if (!supported) {
                door.checkDoorPositionCapabilities(
                    onComplete = { config, areas -> onSession(id) {
                        val bounds = areas[area]
                        if (!supportsDoorRead(config.accessType.name, bounds != null)) publish(readout.copy(state = "UNSUPPORTED", detail = "지원 안 됨 · 접근 ${config.accessType} · 영역 ROW_1_LEFT"))
                        else {
                            supported = true
                            publish(readout.copy(state = "WAITING", detail = "지원 확인: ${config.accessType}, 범위 ${bounds?.minValue}..${bounds?.maxValue} · 2초 간격 조회"))
                            query()
                        }
                    } },
                    onFailed = { e -> onSession(id) { fail(e) } }
                )
            } else {
                door.getCurrentDoorPosition(
                    area = area,
                    onComplete = { value -> onSession(id) {
                        if (value == null) publish(readout.copy(state = "UNAVAILABLE", detail = "SDK가 null 반환 · 0으로 대체하지 않음"))
                        else {
                            val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
                            publish(readout.copy(state = "LIVE", value = value, receivedElapsed = SystemClock.elapsedRealtime(), receivedUtc = utc, detail = "공식 getCurrentDoorPosition · 수신 시각(차량 측 측정 시각 아님)"))
                        }
                    } },
                    onFailed = { e -> onSession(id) { fail(e) } }
                )
            }
        } catch (e: Exception) { pending = false; fail(e) }
    }
    private fun fail(e: Exception) {
        supported = false
        publish(readout.copy(state = "ERROR", detail = "조회 실패(미지원과 구분): ${e.javaClass.simpleName}: ${e.message}"))
    }
}
