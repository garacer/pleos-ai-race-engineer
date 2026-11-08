package com.jev.drivecoach

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One radio message: what is shown and what is said. */
data class DebriefCard(
    val id: String,
    val eyebrow: String,
    val headline: String,
    val sub: String,
    val spoken: String,
)

/**
 * Turns a [Briefing] into five short engineer messages.
 * Order: session -> potential -> where time went -> what went well -> one mission.
 */
object Debrief {
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", abs(v))
    fun clock(s: Double) = String.format(Locale.US, "%d:%04.1f", (s / 60).toInt(), s % 60)
    /** "1분 40초 4" — how a Korean engineer reads a lap time on the radio. */
    fun say(s: Double): String {
        val tenths = (s * 10).roundToInt()
        val m = tenths / 600; val sec = (tenths % 600) / 10; val t = tenths % 10
        return "${m}분 ${sec}초 $t"
    }

    /** Mission target: the best sector set on a flying lap (an in-lap sector is real but not a fair target). */
    fun missionTarget(b: Briefing, sector: Int): SectorBest {
        val k = sector - 1
        val flying = b.rows.filter { it.kind == LapKind.FLYING && it.sectorLoss[k] != null }
        val r = flying.minByOrNull { listOf(it.lap.s1, it.lap.s2, it.lap.s3)[k] } ?: return b.bestSectors[k]
        return SectorBest(sector, listOf(r.lap.s1, r.lap.s2, r.lap.s3)[k], r.lap.number)
    }

    fun cards(b: Briefing, loss: TimeLossSegment?, corners: List<CornerSnapshot>): List<DebriefCard> {
        val gap = b.best.seconds - b.theoretical
        val sector = b.focusSector + 1
        val target = missionTarget(b, sector)
        val bestSec = listOf(b.best.s1, b.best.s2, b.best.s3)[b.focusSector]
        val compared = corners.filter { it.subjectSpeed != null && it.referenceSpeed != null }
        val faster = compared.filter { it.subjectSpeed!! > it.referenceSpeed!! }
        val top = faster.maxByOrNull { it.subjectSpeed!! - it.referenceSpeed!! }
        val out = mutableListOf(
            DebriefCard("session", "오늘 세션", clock(b.best.seconds),
                "${b.best.number}랩이 베스트. 플라잉 랩 ${b.flyingCount}개 중 가장 빨랐어요.",
                "수고했어요. 오늘 베스트는 ${b.best.number}랩, ${say(b.best.seconds)}."),
            DebriefCard("potential", "남은 시간", "${f1(gap)}초",
                "섹터 베스트만 이어 붙여도 ${clock(b.theoretical)}. 이미 달려 본 기록이에요.",
                "섹터 베스트만 이으면 ${say(b.theoretical)}. 아직 ${f1(gap)}초 남았어요."),
        )
        if (loss != null) out += DebriefCard("loss", "어디서", "턴 ${loss.fromTurn} → 턴 ${loss.toTurn}",
            "${b.best.number}랩은 이 구간에서 4랩보다 ${f1(loss.lost)}초 늦었어요.",
            "시간은 턴 ${loss.fromTurn}에서 턴 ${loss.toTurn} 사이에서 잃었어요. 4랩보다 ${f1(loss.lost)}초 늦었어요.")
        if (top != null) out += DebriefCard("good", "잘한 점", "코너 속도 ${faster.size}/${compared.size}",
            "비교한 코너 ${compared.size}곳 중 ${faster.size}곳에서 4랩보다 속도가 높았어요. 가장 큰 차이는 턴 ${top.turn}, +${(top.subjectSpeed!! - top.referenceSpeed!!).roundToInt()} km/h.",
            "코너 속도는 좋았어요. ${compared.size}곳 중 ${faster.size}곳에서 4랩보다 빨랐어요. 이건 그대로 가져가요.")
        out += DebriefCard("mission", "다음 세션 미션", "섹터 $sector, ${target.lap}랩처럼",
            "목표 ${f1(target.seconds)}초 (이번 베스트 랩 ${f1(bestSec)}초). 다른 건 지금처럼.",
            "다음 세션 미션은 하나. 섹터 ${sector}를 ${target.lap}랩처럼, ${f1(target.seconds)}초. 나머지는 지금처럼.")
        return out
    }
}

/** Hand-written JSON (testable on the JVM). */
object DebriefJson {
    private fun s(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    private fun n(v: Double) = String.format(Locale.US, "%.3f", v)
    fun build(b: Briefing, loss: TimeLossSegment?, cards: List<DebriefCard>): String {
        val c = cards.joinToString(",") { "{\"id\":${s(it.id)},\"eyebrow\":${s(it.eyebrow)},\"headline\":${s(it.headline)},\"sub\":${s(it.sub)},\"spoken\":${s(it.spoken)}}" }
        val laps = b.rows.joinToString(",") { "{\"lap\":${it.lap.number},\"time\":${n(it.lap.seconds)},\"kind\":${s(it.kind.name)}}" }
        val l = loss?.let { "{\"from\":${it.fromTurn},\"to\":${it.toTurn}}" } ?: "null"
        return "{\"best\":${b.best.number},\"focus\":${b.focusSector + 1},\"loss\":$l,\"laps\":[$laps],\"cards\":[$c]}"
    }
}
