package com.jev.drivecoach

data class DoorReadout(
    val state: String = "WAITING",
    val value: Int? = null,
    val receivedElapsed: Long? = null,
    val receivedUtc: String? = null,
    val detail: String = "서비스 준비 확인 중"
) {
    fun freshness(now: Long): String = when {
        state != "LIVE" -> state
        value == null || receivedElapsed == null -> "UNAVAILABLE"
        now - receivedElapsed > 6000 -> "STALE"
        else -> "LIVE"
    }
    fun display(now: Long): String {
        val validity = freshness(now)
        val age = receivedElapsed?.let { ((now - it).coerceAtLeast(0) / 1000).toString() + "초" } ?: "없음"
        val shown = if (validity == "LIVE") value.toString() else "현재값 미확인" + (value?.let { " · 마지막 수신값 $it" } ?: "")
        return "Pleos Vehicle SDK 2.3.2 · 에뮬레이터 (실차 아님)\n운전석 문 위치: $shown\n상태: $validity · 단위: SDK position enum\n수신 UTC: ${receivedUtc ?: "없음"} · 수신 후 $age\n$detail"
    }
}

internal fun supportsDoorRead(access: String, selectedAreaPresent: Boolean): Boolean = selectedAreaPresent && access in listOf("READ", "READ_WRITE")
