package com.jev.drivecoach

import java.util.Locale

data class LapRecord(val number: Int, val seconds: Double, val s1: Double, val s2: Double, val s3: Double)
data class LapCoach(val shortText: String, val detail: String, val voiceText: String)
object ReplayCoach {
    fun time(s: Double) = String.format(Locale.US, "%d:%04.1f", (s / 60).toInt(), s % 60)
    private fun delta(v: Double) = String.format(Locale.US, "%+.1f", v)
    fun describe(laps: List<LapRecord>, index: Int): LapCoach {
        val lap = laps[index]
        val prior = laps.take(index).minByOrNull { it.seconds }
        val result = when {
            prior == null -> "첫 기준 기록입니다."
            lap.seconds < prior.seconds - 0.05 -> "이전 최고보다 ${String.format(Locale.US, "%.1f", prior.seconds-lap.seconds)}초 단축."
            else -> "이전 최고보다 ${String.format(Locale.US, "%.1f", lap.seconds-prior.seconds)}초 늦음."
        }
        val short = "기록 재생. ${lap.number}랩 ${time(lap.seconds)}. $result"
        val sectors = "섹터(초): ${lap.s1} / ${lap.s2} / ${lap.s3}"
        val comparison = prior?.let { "이전 베스트 ${it.number}랩 대비: 전체 ${delta(lap.seconds-it.seconds)}초\nS1 ${delta(lap.s1-it.s1)} / S2 ${delta(lap.s2-it.s2)} / S3 ${delta(lap.s3-it.s3)}초" } ?: "이전 베스트 없음"
        val unknown = if(lap.number == 9) "\n9랩 S3 137.7초: 원인 미확인" else ""
        return LapCoach(short, "$sectors\n$comparison$unknown\n출처: 2026-10-04 오후 기록 재생 · 실시간 랩/섹터 아님", "기록 재생. ${lap.number}랩, ${(lap.seconds/60).toInt()}분 ${String.format(Locale.US, "%.1f", lap.seconds%60)}초. $result")
    }
}
class ReplaySession(private val size: Int) {
    var index = 0; private set
    var state = "STOPPED"; private set
    var runId = 0; private set
    fun play() { if(state == "STOPPED" || state == "COMPLETE") { index=0; runId++ }; state="PLAYING" }
    fun pause() { if(state=="PLAYING") state="PAUSED" }
    fun stop() { state="STOPPED" }
    fun restart() { index=0;runId++;state="PLAYING" }
    fun next() { index=(index+1)%size }
    fun previous() { index=(index+size-1)%size }
    fun tick() { if(state=="PLAYING") { if(index<size-1) index++ else state="COMPLETE" } }
}
class VoiceGate {
    private var lastAt = Long.MIN_VALUE
    private val keys = mutableSetOf<String>()
    fun accept(key: String, now: Long): Boolean {
        if(key in keys || (lastAt != Long.MIN_VALUE && now-lastAt<2500)) return false
        keys.add(key);lastAt=now;return true
    }
}
internal fun offlineKorean(language: String, networkRequired: Boolean) = language in listOf("ko", "kor") && !networkRequired
internal fun detailsAllowed(requiresOptimization: Boolean?, activeRestrictions: Int?) = requiresOptimization == false && activeRestrictions == 0
