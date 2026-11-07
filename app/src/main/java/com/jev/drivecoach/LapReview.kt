package com.jev.drivecoach

import java.util.Locale
import kotlin.math.abs

enum class CompareReference { PRIOR_BEST, SESSION_BEST }
data class LapComparison(val reference: LapRecord?, val totalDelta: Double?, val sectorDeltas: List<Double>, val valid: Boolean, val caution: Boolean)
object LapReview {
    private fun valid(l:LapRecord) = listOf(l.seconds,l.s1,l.s2,l.s3).all { it.isFinite() && it > 0 } && abs(l.seconds-l.s1-l.s2-l.s3) <= 0.15
    fun compare(laps:List<LapRecord>, index:Int, mode:CompareReference):LapComparison {
        val lap=laps[index]
        val pool=if(mode==CompareReference.PRIOR_BEST) laps.take(index) else laps
        val reference=pool.filter { valid(it) }.minByOrNull { it.seconds }
        val ok=valid(lap)
        return LapComparison(reference,if(ok && reference!=null)lap.seconds-reference.seconds else null,
            if(ok && reference!=null)listOf(lap.s1-reference.s1,lap.s2-reference.s2,lap.s3-reference.s3)else emptyList(),ok,lap.number==9)
    }
    private fun delta(d:Double) = String.format(Locale.US,"%+.1f",if(abs(d)<0.05)0.0 else d)
    fun text(laps:List<LapRecord>,index:Int,mode:CompareReference):String {
        val c=compare(laps,index,mode)
        val label=if(mode==CompareReference.PRIOR_BEST)"이전 베스트" else "세션 베스트 · 전체 9랩 사후 비교"
        if(!c.valid)return "$label\n기록 유효성 확인 필요 · 수치 코칭 보류"
        val r=c.reference?:return "$label\n첫 랩 · 비교할 이전 기록 없음"
        val deltas=c.sectorDeltas
        val numbers="기준 ${r.number}랩 ${ReplayCoach.time(r.seconds)} · 전체 ${delta(c.totalDelta!!)}초\n"+deltas.mapIndexed { i,d -> "S${i+1} ${delta(d)}" }.joinToString(" / ")+"초"
        val focus=when {
            c.caution -> "9랩 S3 137.7초 · 원인 미확정. 구간 원본을 먼저 확인하세요. 성능 개선 원인으로 해석하지 않습니다."
            r.number==laps[index].number -> "현재 랩이 비교 기준입니다."
            deltas.maxOrNull()!!>0.05 -> { val i=deltas.indices.maxByOrNull { deltas[it] }!!;"비교 우선 구간 S${i+1}: 기준보다 ${String.format(Locale.US,"%.1f",deltas[i])}초 길었습니다. 해당 구간 기록을 먼저 검토하세요." }
            else -> "모든 구간이 기준과 같거나 짧았습니다."
        }
        return "$label\n$numbers\n$focus\n랩·구간 시간만으로 제동·조향 원인은 판단할 수 없습니다."
    }
}
